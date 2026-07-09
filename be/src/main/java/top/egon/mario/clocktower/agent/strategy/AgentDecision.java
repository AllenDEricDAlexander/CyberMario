package top.egon.mario.clocktower.agent.strategy;

import java.util.Map;

public record AgentDecision(
        AgentIntent intent,
        String reasoningSummary,
        Map<String, Object> diagnostics
) {
}
