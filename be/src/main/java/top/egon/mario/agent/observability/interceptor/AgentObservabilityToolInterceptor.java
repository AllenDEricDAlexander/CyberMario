package top.egon.mario.agent.observability.interceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.egon.mario.agent.observability.po.enums.AgentRunEventStatus;
import top.egon.mario.agent.observability.po.enums.AgentRunEventType;
import top.egon.mario.agent.observability.po.enums.AgentRunToolType;
import top.egon.mario.agent.observability.service.AgentRunAuditService;
import top.egon.mario.agent.observability.service.model.AgentRunAuditContext;
import top.egon.mario.agent.observability.service.model.AgentRunEventRecord;
import top.egon.mario.common.utils.LogUtil;

import java.time.Duration;
import java.time.Instant;

/**
 * Records ReAct tool input and output payloads into the agent run timeline.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentObservabilityToolInterceptor extends ToolInterceptor {

    private final AgentRunAuditService auditService;
    @SuppressWarnings("unused")
    private final ObjectMapper objectMapper;

    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        AgentRunAuditContext context = context(request);
        if (context == null) {
            return handler.call(request);
        }
        Instant startedAt = Instant.now();
        AgentRunAuditContext.ToolDescriptor descriptor = descriptor(context, request.getToolName());
        Integer reactRound = context.reactRound() == null ? null : context.reactRound().get();
        safeRecord(context, AgentRunEventRecord.builder(AgentRunEventType.TOOL_REQUEST)
                .reactRound(reactRound)
                .toolName(request.getToolName())
                .toolCallId(request.getToolCallId())
                .toolType(toolType(descriptor))
                .mcpServerCode(mcpServerCode(descriptor))
                .toolArguments(request.getArguments())
                .status(AgentRunEventStatus.STARTED)
                .startedAt(startedAt)
                .finishedAt(startedAt)
                .durationMs(0L)
                .build());
        try {
            ToolCallResponse response = handler.call(request);
            Instant finishedAt = Instant.now();
            AgentRunEventStatus status = response != null && response.isError()
                    ? AgentRunEventStatus.FAILED
                    : AgentRunEventStatus.SUCCESS;
            safeRecord(context, AgentRunEventRecord.builder(AgentRunEventType.TOOL_RESPONSE)
                    .reactRound(reactRound)
                    .toolName(request.getToolName())
                    .toolCallId(request.getToolCallId())
                    .toolType(toolType(descriptor))
                    .mcpServerCode(mcpServerCode(descriptor))
                    .toolArguments(request.getArguments())
                    .toolResult(response == null ? null : response.getResult())
                    .status(status)
                    .metadataJson(response == null ? null : String.valueOf(response.getMetadata()))
                    .startedAt(startedAt)
                    .finishedAt(finishedAt)
                    .durationMs(durationMs(startedAt, finishedAt))
                    .build());
            return response;
        } catch (RuntimeException e) {
            Instant finishedAt = Instant.now();
            safeRecord(context, AgentRunEventRecord.builder(AgentRunEventType.TOOL_RESPONSE)
                    .reactRound(reactRound)
                    .toolName(request.getToolName())
                    .toolCallId(request.getToolCallId())
                    .toolType(toolType(descriptor))
                    .mcpServerCode(mcpServerCode(descriptor))
                    .toolArguments(request.getArguments())
                    .status(AgentRunEventStatus.FAILED)
                    .errorCode(e.getClass().getName())
                    .errorMessage(e.getMessage())
                    .startedAt(startedAt)
                    .finishedAt(finishedAt)
                    .durationMs(durationMs(startedAt, finishedAt))
                    .build());
            return ToolCallResponse.error(request.getToolCallId(), request.getToolName(), e.getMessage());
        }
    }

    @Override
    public String getName() {
        return "AgentObservabilityToolInterceptor";
    }

    private AgentRunAuditContext context(ToolCallRequest request) {
        if (request == null || request.getContext() == null) {
            return null;
        }
        Object value = request.getContext().get(AgentRunAuditContext.METADATA_KEY);
        return value instanceof AgentRunAuditContext context ? context : null;
    }

    private AgentRunAuditContext.ToolDescriptor descriptor(AgentRunAuditContext context, String toolName) {
        AgentRunAuditContext.ToolDescriptor descriptor = context.toolDescriptor(toolName);
        return descriptor == null ? new AgentRunAuditContext.ToolDescriptor(AgentRunToolType.LOCAL, null) : descriptor;
    }

    private AgentRunToolType toolType(AgentRunAuditContext.ToolDescriptor descriptor) {
        return descriptor == null || descriptor.toolType() == null ? AgentRunToolType.UNKNOWN : descriptor.toolType();
    }

    private String mcpServerCode(AgentRunAuditContext.ToolDescriptor descriptor) {
        return descriptor == null ? null : descriptor.mcpServerCode();
    }

    private long durationMs(Instant startedAt, Instant finishedAt) {
        return Duration.between(startedAt, finishedAt).toMillis();
    }

    private void safeRecord(AgentRunAuditContext context, AgentRunEventRecord event) {
        try {
            auditService.record(context, event);
            LogUtil.debug(log).log("agent tool audit payload, runId={}, eventType={}, toolName={}, input={}, output={}",
                    context.runId(), event.eventType(), event.toolName(), event.toolArguments(), event.toolResult());
        } catch (RuntimeException e) {
            LogUtil.error(log).log("agent tool audit write failed, runId={}, toolName={}",
                    context.runId(), event.toolName(), e);
        }
    }
}
