package top.egon.mario.agent.observability.dto.response;

import top.egon.mario.agent.model.dto.enums.ModelProviderType;
import top.egon.mario.agent.observability.po.enums.AgentRunEventStatus;
import top.egon.mario.agent.observability.po.enums.AgentRunEventType;
import top.egon.mario.agent.observability.po.enums.AgentRunToolType;

import java.time.Instant;

public record AgentRunEventAuditResponse(
        Long id,
        Long runId,
        String requestId,
        String traceId,
        String threadId,
        Integer seqNo,
        AgentRunEventType eventType,
        Integer reactRound,
        String toolCallId,
        String toolName,
        AgentRunToolType toolType,
        String mcpServerCode,
        AgentRunEventStatus status,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        ModelProviderType modelProvider,
        String modelName,
        String promptText,
        String requestMessagesJson,
        String requestOptionsJson,
        String availableToolsJson,
        String responseText,
        String toolArguments,
        String toolResult,
        String metadataJson,
        String errorCode,
        String errorMessage,
        Instant createdAt
) {
}
