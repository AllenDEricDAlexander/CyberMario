package top.egon.mario.agent.externalim.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.agent.externalim.ExternalChatException;
import top.egon.mario.agent.externalim.adapter.ExternalChatAdapterRegistry;
import top.egon.mario.agent.externalim.guard.ChatGuardDecision;
import top.egon.mario.agent.externalim.model.ChatInvocation;
import top.egon.mario.agent.externalim.model.ChatSource;
import top.egon.mario.agent.externalim.model.ExternalChatMessage;
import top.egon.mario.agent.externalim.model.ExternalReplyCommand;
import top.egon.mario.agent.externalim.model.ExternalReplyResult;
import top.egon.mario.agent.externalim.runtime.po.ExternalChatEventPo;
import top.egon.mario.agent.externalim.runtime.po.enums.ExternalChatProcessingStatus;
import top.egon.mario.agent.externalim.runtime.repository.ExternalChatEventRepository;
import top.egon.mario.agent.memory.po.AgentMemoryMessagePo;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageRole;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageStatus;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageType;
import top.egon.mario.agent.memory.service.AgentMemoryMessageService;
import top.egon.mario.agent.service.ChatAgentService;
import top.egon.mario.pojo.response.ChatResponse;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class ExternalChatEventExecutionService {

    private final ExternalChatEventRepository repository;
    private final ObjectMapper objectMapper;
    private final ChatAgentService chatAgentService;
    private final AgentMemoryMessageService memoryMessageService;
    private final ExternalChatAdapterRegistry adapterRegistry;
    private final ExternalChatEventStateService stateService;
    private final ExternalChatWorkerProperties properties;

    public ExternalChatEventExecutionService(
            ExternalChatEventRepository repository,
            ObjectMapper objectMapper,
            ChatAgentService chatAgentService,
            AgentMemoryMessageService memoryMessageService,
            ExternalChatAdapterRegistry adapterRegistry,
            ExternalChatEventStateService stateService,
            ExternalChatWorkerProperties properties) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.chatAgentService = chatAgentService;
        this.memoryMessageService = memoryMessageService;
        this.adapterRegistry = adapterRegistry;
        this.stateService = stateService;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.NEVER)
    public void execute(Long eventId, String workerId) {
        ExternalChatEventPo event = repository.findById(eventId)
                .filter(value -> value.getProcessingStatus() == ExternalChatProcessingStatus.RUNNING)
                .filter(value -> workerId.equals(value.getLockedBy()))
                .orElseThrow(() -> new ExternalChatException("EXTERNAL_CHAT_EVENT_CLAIM_LOST",
                        "external chat event claim is no longer valid"));
        try {
            ExternalChatMessage message = readMessage(event.getNormalizedMessageJson());
            AgentMemoryMessagePo assistant = successfulAssistant(event).orElse(null);
            if (assistant == null) {
                ChatInvocation invocation = invocation(event, message);
                List<ChatResponse> chunks = chatAgentService.chat(invocation).collectList().block();
                if (chunks != null && chunks.stream().anyMatch(chunk -> "error".equals(chunk.type()))) {
                    stateService.fail(eventId, workerId, "EXTERNAL_CHAT_AGENT_FAILED",
                            "external chat agent returned an error");
                    return;
                }
                assistant = successfulAssistant(event).orElse(null);
            }
            ExternalChatEventPo refreshed = repository.findById(eventId).orElseThrow();
            if (assistant == null) {
                if (refreshed.getGuardDecision() == ChatGuardDecision.IGNORE) {
                    stateService.markIgnored(eventId, workerId);
                } else {
                    stateService.fail(eventId, workerId, "EXTERNAL_CHAT_EMPTY_REPLY",
                            "chat agent produced no persisted reply");
                }
                return;
            }
            stateService.markCandidate(eventId, workerId, assistant.getId());
            ExternalReplyResult result = adapterRegistry.requireReply(event.getPlatform()).send(
                    new ExternalReplyCommand(event.getConnectorId(), message.conversationId(),
                            message.messageId(), message.audienceKey(), event.getReplyVersion(),
                            assistant.getContent()));
            if (result.sent()) {
                stateService.markSent(eventId, workerId, result.platformMessageId());
            } else if (result.retryable()) {
                stateService.retryReply(eventId, workerId, result.errorCode(), result.errorMessage(),
                        Instant.now().plus(properties.retryDelay()), properties.maxAttempts());
            } else {
                stateService.fail(eventId, workerId, result.errorCode(), result.errorMessage());
            }
        } catch (ExternalChatException error) {
            stateService.fail(eventId, workerId, error.code(), error.getMessage());
        } catch (RuntimeException error) {
            stateService.fail(eventId, workerId, "EXTERNAL_CHAT_EXECUTION_FAILED",
                    error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        }
    }

    private Optional<AgentMemoryMessagePo> successfulAssistant(ExternalChatEventPo event) {
        return memoryMessageService.findExternalMessage(event.getSpaceId(), event.getPlatform(),
                event.getConnectorId(), event.getExternalEventId(), AgentMemoryMessageRole.ASSISTANT,
                AgentMemoryMessageType.MESSAGE, AgentMemoryMessageStatus.SUCCEEDED);
    }

    private ChatInvocation invocation(ExternalChatEventPo event, ExternalChatMessage message) {
        if (event.getOwnerUserId() == null || !StringUtils.hasText(event.getSpaceId())) {
            throw new ExternalChatException("EXTERNAL_CHAT_BINDING_NOT_FOUND",
                    "durable event has no trusted memory space binding");
        }
        return new ChatInvocation(ChatSource.EXTERNAL_IM, message.text(), event.getOwnerUserId(),
                null, null, event.getSpaceId(), event.getPlatform(), event.getConnectorId(),
                message.conversationId(), message.conversationType(), message.audienceKey(),
                message.sender(), message.messageType(), message.mentionedAgent(),
                message.repliedToAgentMessage(), event.getExternalEventId(),
                event.getExternalMessageId(), message.occurredAt());
    }

    private ExternalChatMessage readMessage(String json) {
        try {
            return objectMapper.readValue(json, ExternalChatMessage.class);
        } catch (JsonProcessingException error) {
            throw new ExternalChatException("EXTERNAL_CHAT_EVENT_JSON_INVALID",
                    "durable normalized message is invalid");
        }
    }
}
