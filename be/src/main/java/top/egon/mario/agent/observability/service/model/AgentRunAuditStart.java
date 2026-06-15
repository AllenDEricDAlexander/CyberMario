package top.egon.mario.agent.observability.service.model;

import java.time.Instant;
import java.util.Map;

public record AgentRunAuditStart(
        String requestId,
        String traceId,
        Long userId,
        String username,
        String threadId,
        Long presetId,
        String runtimeFingerprint,
        String effectiveConfigJson,
        String userMessage,
        Map<String, AgentRunAuditContext.ToolDescriptor> toolDescriptors,
        Instant startedAt
) {
}
