package top.egon.mario.agent.dto.response;

import top.egon.mario.agent.po.enums.AgentConversationStatus;

import java.time.Instant;

/**
 * Summary row for a super-admin agent conversation audit page.
 */
public record AgentConversationAuditResponse(
        Long id,
        String requestId,
        String traceId,
        Long userId,
        String username,
        String threadId,
        Long presetId,
        String runtimeFingerprint,
        String effectiveConfigJson,
        AgentConversationStatus status,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        String errorCode,
        String errorMessage,
        String ip,
        String userAgent,
        Instant createdAt
) {
}
