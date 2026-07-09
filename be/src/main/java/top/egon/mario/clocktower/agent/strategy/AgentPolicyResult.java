package top.egon.mario.clocktower.agent.strategy;

import top.egon.mario.clocktower.agent.decision.ClocktowerAgentDecisionPolicyType;
import top.egon.mario.clocktower.agent.decision.ClocktowerAgentDecisionStatus;

import java.util.Map;

public record AgentPolicyResult(
        AgentDecision decision,
        String policyType,
        String status,
        String errorMessage,
        String modelProvider,
        String modelName,
        String promptHash,
        Map<String, Object> metadata
) {

    public AgentPolicyResult {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static AgentPolicyResult heuristic(AgentDecision decision) {
        return new AgentPolicyResult(decision, ClocktowerAgentDecisionPolicyType.HEURISTIC,
                ClocktowerAgentDecisionStatus.ACCEPTED, null, null, null, null, Map.of());
    }
}
