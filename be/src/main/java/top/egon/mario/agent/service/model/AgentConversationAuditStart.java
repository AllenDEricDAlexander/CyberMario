package top.egon.mario.agent.service.model;

import java.time.Instant;

/**
 * Values captured when a streamed agent conversation audit record starts.
 */
public record AgentConversationAuditStart(
        String requestId,
        String traceId,
        Long userId,
        String username,
        String threadId,
        Long presetId,
        String runtimeFingerprint,
        String effectiveConfigJson,
        String ip,
        String userAgent,
        Instant startedAt
) {
}
