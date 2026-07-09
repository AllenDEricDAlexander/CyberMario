package top.egon.mario.clocktower.agent.strategy;

public interface ClocktowerAgentPolicy {

    AgentDecision decide(AgentDecisionContext context);

    default AgentPolicyResult decideWithMetadata(AgentDecisionContext context) {
        return AgentPolicyResult.heuristic(decide(context));
    }
}
