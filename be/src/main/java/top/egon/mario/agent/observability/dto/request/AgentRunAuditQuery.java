package top.egon.mario.agent.observability.dto.request;

import top.egon.mario.agent.observability.po.enums.AgentRunAuditStatus;

import java.time.Instant;

public record AgentRunAuditQuery(
        Instant startAt,
        Instant endAt,
        Long userId,
        String username,
        String threadId,
        String requestId,
        String traceId,
        Long presetId,
        String toolName,
        String mcpServerCode,
        AgentRunAuditStatus status
) {
}
