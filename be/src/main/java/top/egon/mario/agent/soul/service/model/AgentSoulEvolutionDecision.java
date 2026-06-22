package top.egon.mario.agent.soul.service.model;

public record AgentSoulEvolutionDecision(
        boolean shouldUpdate,
        String reason,
        String changeSummary,
        String updatedSoulMd,
        String modelProvider,
        String modelName
) {

    public static AgentSoulEvolutionDecision noUpdate(String reason) {
        return new AgentSoulEvolutionDecision(false, reason, null, null, null, null);
    }
}
