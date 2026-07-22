package top.egon.mario.agent.externalim.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import top.egon.mario.agent.externalim.adapter.ExternalChatAdapterRegistry;
import top.egon.mario.agent.externalim.adapter.ExternalChatReplyPort;
import top.egon.mario.agent.externalim.guard.ChatGuardDecision;
import top.egon.mario.agent.externalim.model.ChatInvocation;
import top.egon.mario.agent.externalim.model.ChatSource;
import top.egon.mario.agent.externalim.model.ExternalChatMessage;
import top.egon.mario.agent.externalim.model.ExternalChatPlatform;
import top.egon.mario.agent.externalim.model.ExternalConversationType;
import top.egon.mario.agent.externalim.model.ExternalMessageType;
import top.egon.mario.agent.externalim.model.ExternalReplyResult;
import top.egon.mario.agent.externalim.model.ExternalSender;
import top.egon.mario.agent.externalim.model.ExternalSenderType;
import top.egon.mario.agent.externalim.runtime.po.ExternalChatEventPo;
import top.egon.mario.agent.externalim.runtime.po.enums.ExternalChatProcessingStatus;
import top.egon.mario.agent.externalim.runtime.po.enums.ExternalChatReplyStatus;
import top.egon.mario.agent.externalim.runtime.repository.ExternalChatEventRepository;
import top.egon.mario.agent.memory.po.AgentMemoryMessagePo;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageRole;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageStatus;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageType;
import top.egon.mario.agent.memory.service.AgentMemoryMessageService;
import top.egon.mario.agent.service.ChatAgentService;
import top.egon.mario.pojo.response.ChatResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ExternalChatEventExecutionServiceTests {

    private final ExternalChatEventRepository repository = mock(ExternalChatEventRepository.class);
    private final ChatAgentService chatAgentService = mock(ChatAgentService.class);
    private final AgentMemoryMessageService memoryMessageService = mock(AgentMemoryMessageService.class);
    private final ExternalChatAdapterRegistry adapterRegistry = mock(ExternalChatAdapterRegistry.class);
    private final ExternalChatEventStateService stateService = mock(ExternalChatEventStateService.class);
    private final ExternalChatReplyPort replyPort = mock(ExternalChatReplyPort.class);
    private final ExternalChatWorkerProperties properties = new ExternalChatWorkerProperties(
            true, 20, 3, Duration.ZERO, Duration.ofSeconds(1),
            Duration.ofSeconds(5), Duration.ofMinutes(2));
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ExternalChatEventExecutionService executionService = new ExternalChatEventExecutionService(
            repository, objectMapper, chatAgentService, memoryMessageService,
            adapterRegistry, stateService, properties);

    @Test
    void firstExecutionCallsSingleChatServiceThenSendsPersistedAssistant() throws Exception {
        ExternalChatEventPo event = runningEvent();
        AgentMemoryMessagePo assistant = assistant(501L, "answer");
        given(repository.findById(10L)).willReturn(Optional.of(event));
        given(memoryMessageService.findExternalMessage("space-1", ExternalChatPlatform.TELEGRAM,
                "main", "update-1", AgentMemoryMessageRole.ASSISTANT,
                AgentMemoryMessageType.MESSAGE, AgentMemoryMessageStatus.SUCCEEDED))
                .willReturn(Optional.empty(), Optional.of(assistant));
        given(chatAgentService.chat(any(ChatInvocation.class)))
                .willReturn(Flux.just(new ChatResponse("__external_im__:space-1", "answer", "message")));
        given(adapterRegistry.requireReply(ExternalChatPlatform.TELEGRAM)).willReturn(replyPort);
        given(replyPort.send(any())).willReturn(ExternalReplyResult.sent("telegram-message-9"));

        executionService.execute(10L, "worker-1");

        verify(chatAgentService).chat(org.mockito.ArgumentMatchers.argThat(invocation ->
                invocation.ownerUserId().equals(8L)
                        && invocation.memorySpaceId().equals("space-1")
                        && invocation.source() == ChatSource.EXTERNAL_IM));
        verify(stateService).markCandidate(10L, "worker-1", 501L);
        verify(stateService).markSent(10L, "worker-1", "telegram-message-9");
    }

    @Test
    void replyRetryUsesPersistedCandidateWithoutRunningChatAgain() throws Exception {
        ExternalChatEventPo event = runningEvent();
        event.setAssistantMessageId(501L);
        event.setReplyStatus(ExternalChatReplyStatus.RETRY_PENDING);
        given(repository.findById(10L)).willReturn(Optional.of(event));
        given(memoryMessageService.findExternalMessage(anyString(), any(), anyString(), anyString(),
                eq(AgentMemoryMessageRole.ASSISTANT), eq(AgentMemoryMessageType.MESSAGE),
                eq(AgentMemoryMessageStatus.SUCCEEDED)))
                .willReturn(Optional.of(assistant(501L, "same candidate")));
        given(adapterRegistry.requireReply(ExternalChatPlatform.TELEGRAM)).willReturn(replyPort);
        given(replyPort.send(any())).willReturn(ExternalReplyResult.sent("telegram-message-10"));

        executionService.execute(10L, "worker-1");

        verifyNoInteractions(chatAgentService);
        verify(replyPort).send(org.mockito.ArgumentMatchers.argThat(command ->
                command.text().equals("same candidate") && command.replyVersion() == 1));
    }

    @Test
    void ignoredGuardDoesNotCallReplyPort() throws Exception {
        ExternalChatEventPo event = runningEvent();
        event.setGuardDecision(ChatGuardDecision.IGNORE);
        given(repository.findById(10L)).willReturn(Optional.of(event));
        given(memoryMessageService.findExternalMessage(anyString(), any(), anyString(), anyString(),
                eq(AgentMemoryMessageRole.ASSISTANT), eq(AgentMemoryMessageType.MESSAGE),
                eq(AgentMemoryMessageStatus.SUCCEEDED)))
                .willReturn(Optional.empty());
        given(chatAgentService.chat(any(ChatInvocation.class))).willReturn(Flux.empty());

        executionService.execute(10L, "worker-1");

        verify(stateService).markIgnored(10L, "worker-1");
        verify(adapterRegistry, never()).requireReply(any());
    }

    private ExternalChatEventPo runningEvent() throws Exception {
        ExternalChatMessage message = new ExternalChatMessage("update-1", "message-1",
                ExternalChatPlatform.TELEGRAM, "main", "group-1", ExternalConversationType.GROUP,
                "telegram:main:group-1", new ExternalSender("sender-1", "Mario", ExternalSenderType.HUMAN),
                ExternalMessageType.TEXT, "hello", false, false,
                Instant.parse("2026-07-20T00:00:00Z"));
        ExternalChatEventPo event = new ExternalChatEventPo();
        event.setId(10L);
        event.setPlatform(ExternalChatPlatform.TELEGRAM);
        event.setConnectorId("main");
        event.setExternalEventId("update-1");
        event.setExternalMessageId("message-1");
        event.setSpaceId("space-1");
        event.setOwnerUserId(8L);
        event.setNormalizedMessageJson(objectMapper.writeValueAsString(message));
        event.setProcessingStatus(ExternalChatProcessingStatus.RUNNING);
        event.setReplyStatus(ExternalChatReplyStatus.NOT_REQUIRED);
        event.setReplyVersion(1);
        event.setLockedBy("worker-1");
        return event;
    }

    private AgentMemoryMessagePo assistant(Long id, String content) {
        AgentMemoryMessagePo assistant = new AgentMemoryMessagePo();
        assistant.setId(id);
        assistant.setRole(AgentMemoryMessageRole.ASSISTANT);
        assistant.setMessageType(AgentMemoryMessageType.MESSAGE);
        assistant.setMessageStatus(AgentMemoryMessageStatus.SUCCEEDED);
        assistant.setContent(content);
        return assistant;
    }
}
