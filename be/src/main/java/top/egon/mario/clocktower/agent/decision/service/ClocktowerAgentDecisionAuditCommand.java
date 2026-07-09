package top.egon.mario.clocktower.agent.decision.service;

public record ClocktowerAgentDecisionAuditCommand(
        Long gameId,
        Long agentInstanceId,
        Long gameSeatId,
        Long triggerTaskId,
        String phase,
        int dayNo,
        int nightNo,
        String decisionType,
        String policyType,
        Object legalIntents,
        Object selectedIntent,
        String reasoningSummary,
        String modelProvider,
        String modelName,
        String promptHash,
        String status,
        String errorMessage,
        Object metadata
) {
}
