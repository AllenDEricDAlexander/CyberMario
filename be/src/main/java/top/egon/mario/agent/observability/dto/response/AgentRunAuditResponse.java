package top.egon.mario.agent.observability.dto.response;

import top.egon.mario.agent.observability.po.enums.AgentRunAuditStatus;

import java.time.Instant;

public record AgentRunAuditResponse(
        Long id,
        String requestId,
        String traceId,
        String threadId,
        Long userId,
        String username,
        Long presetId,
        String runtimeFingerprint,
        String effectiveConfigJson,
        String userMessage,
        String finalMessage,
        String finalThinking,
        AgentRunAuditStatus status,
        Integer modelCallCount,
        Integer toolCallCount,
        Integer mcpToolCallCount,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        String errorCode,
        String errorMessage,
        Instant createdAt
) {
}
