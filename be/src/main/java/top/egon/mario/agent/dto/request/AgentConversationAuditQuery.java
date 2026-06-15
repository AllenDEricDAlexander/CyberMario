package top.egon.mario.agent.dto.request;

import top.egon.mario.agent.po.enums.AgentConversationStatus;

import java.time.Instant;

/**
 * Query filters for super-admin agent conversation audit pages.
 */
public record AgentConversationAuditQuery(
        Instant startAt,
        Instant endAt,
        Long userId,
        String username,
        String threadId,
        Long presetId,
        AgentConversationStatus status
) {
}
