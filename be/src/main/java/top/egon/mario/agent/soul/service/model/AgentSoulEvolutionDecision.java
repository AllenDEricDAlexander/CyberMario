package top.egon.mario.agent.soul.service.model;

public record AgentSoulEvolutionDecision(
        boolean shouldUpdate,
        String reason,
        String changeSummary,
        String updatedSoulMd,
        String modelProvider,
        String modelName,
        boolean failureNoUpdate
) {

    public AgentSoulEvolutionDecision(boolean shouldUpdate, String reason, String changeSummary,
                                      String updatedSoulMd, String modelProvider, String modelName) {
        this(shouldUpdate, reason, changeSummary, updatedSoulMd, modelProvider, modelName, false);
    }

    public static AgentSoulEvolutionDecision noUpdate(String reason) {
        return new AgentSoulEvolutionDecision(false, reason, null, null, null, null, false);
    }

    public static AgentSoulEvolutionDecision noUpdateFailure(String reason) {
        return new AgentSoulEvolutionDecision(false, reason, null, null, null, null, true);
    }
}
