package top.egon.mario.agent.observability.interceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import top.egon.mario.agent.observability.po.enums.AgentRunEventStatus;
import top.egon.mario.agent.observability.po.enums.AgentRunEventType;
import top.egon.mario.agent.observability.service.AgentRunAuditService;
import top.egon.mario.agent.observability.service.model.AgentRunAuditContext;
import top.egon.mario.agent.observability.service.model.AgentRunEventRecord;
import top.egon.mario.common.utils.LogUtil;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records ReAct model prompts and outputs into the agent run timeline.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentObservabilityModelInterceptor extends ModelInterceptor {

    private final AgentRunAuditService auditService;
    private final ObjectMapper objectMapper;

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        AgentRunAuditContext context = context(request);
        if (context == null) {
            return handler.call(request);
        }
        int round = context.nextReactRound();
        Instant startedAt = Instant.now();
        safeRecord(context, AgentRunEventRecord.builder(AgentRunEventType.MODEL_REQUEST)
                .reactRound(round)
                .status(AgentRunEventStatus.SUCCESS)
                .modelName(modelName(request))
                .promptText(promptText(request))
                .requestMessagesJson(toJson(messagesPayload(request)))
                .requestOptionsJson(toJson(request.getOptions()))
                .availableToolsJson(toJson(toolsPayload(request)))
                .startedAt(startedAt)
                .finishedAt(startedAt)
                .durationMs(0L)
                .build());
        try {
            ModelResponse response = handler.call(request);
            Instant finishedAt = Instant.now();
            safeRecord(context, AgentRunEventRecord.builder(AgentRunEventType.MODEL_RESPONSE)
                    .reactRound(round)
                    .status(AgentRunEventStatus.SUCCESS)
                    .modelName(modelName(request))
                    .responseText(responseText(response))
                    .metadataJson(responseMetadata(response))
                    .startedAt(startedAt)
                    .finishedAt(finishedAt)
                    .durationMs(durationMs(startedAt, finishedAt))
                    .build());
            return response;
        } catch (RuntimeException e) {
            Instant finishedAt = Instant.now();
            safeRecord(context, AgentRunEventRecord.builder(AgentRunEventType.MODEL_RESPONSE)
                    .reactRound(round)
                    .status(AgentRunEventStatus.FAILED)
                    .modelName(modelName(request))
                    .errorCode(e.getClass().getName())
                    .errorMessage(e.getMessage())
                    .startedAt(startedAt)
                    .finishedAt(finishedAt)
                    .durationMs(durationMs(startedAt, finishedAt))
                    .build());
            throw e;
        }
    }

    @Override
    public String getName() {
        return "AgentObservabilityModelInterceptor";
    }

    private AgentRunAuditContext context(ModelRequest request) {
        if (request == null || request.getContext() == null) {
            return null;
        }
        Object value = request.getContext().get(AgentRunAuditContext.METADATA_KEY);
        return value instanceof AgentRunAuditContext context ? context : null;
    }

    private String promptText(ModelRequest request) {
        StringBuilder builder = new StringBuilder();
        if (request.getSystemMessage() != null) {
            builder.append("[system]\n").append(request.getSystemMessage().getText()).append('\n');
        }
        if (request.getMessages() != null) {
            for (Message message : request.getMessages()) {
                builder.append('[').append(message.getMessageType()).append("]\n")
                        .append(message.getText()).append('\n');
            }
        }
        return builder.toString();
    }

    private List<Map<String, Object>> messagesPayload(ModelRequest request) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (request.getSystemMessage() != null) {
            rows.add(messagePayload(request.getSystemMessage()));
        }
        if (request.getMessages() != null) {
            request.getMessages().forEach(message -> rows.add(messagePayload(message)));
        }
        return rows;
    }

    private Map<String, Object> messagePayload(Message message) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", message.getMessageType());
        row.put("text", message.getText());
        if (message.getMetadata() != null && !message.getMetadata().isEmpty()) {
            row.put("metadata", message.getMetadata());
        }
        return row;
    }

    private Map<String, Object> toolsPayload(ModelRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tools", request.getTools());
        payload.put("descriptions", request.getToolDescriptions());
        return payload;
    }

    private String responseText(ModelResponse response) {
        if (response == null || response.getMessage() == null) {
            return null;
        }
        Object message = response.getMessage();
        if (message instanceof AssistantMessage assistantMessage) {
            return assistantMessage.getText();
        }
        if (message instanceof ChatResponse chatResponse && chatResponse.getResult() != null
                && chatResponse.getResult().getOutput() != null) {
            return chatResponse.getResult().getOutput().getText();
        }
        return String.valueOf(message);
    }

    private String responseMetadata(ModelResponse response) {
        if (response == null) {
            return null;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (response.getChatResponse() != null) {
            metadata.put("chatResponseMetadata", response.getChatResponse().getMetadata());
        }
        Object message = response.getMessage();
        if (message instanceof AssistantMessage assistantMessage && assistantMessage.hasToolCalls()) {
            metadata.put("toolCalls", assistantMessage.getToolCalls());
        }
        return metadata.isEmpty() ? null : toJson(metadata);
    }

    private String modelName(ModelRequest request) {
        return request == null || request.getOptions() == null ? null : request.getOptions().getModel();
    }

    private long durationMs(Instant startedAt, Instant finishedAt) {
        return Duration.between(startedAt, finishedAt).toMillis();
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private void safeRecord(AgentRunAuditContext context, AgentRunEventRecord event) {
        try {
            auditService.record(context, event);
            LogUtil.debug(log).log("agent model audit payload, runId={}, eventType={}, prompt={}, messages={}, response={}",
                    context.runId(), event.eventType(), event.promptText(), event.requestMessagesJson(),
                    event.responseText());
        } catch (RuntimeException e) {
            LogUtil.error(log).log("agent model audit write failed, runId={}", context.runId(), e);
        }
    }
}
