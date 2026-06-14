package top.egon.mario.agent.model.dto.response;

/**
 * Aggregated metric for one provider, model, scenario or status.
 */
public record ModelAuditDimensionStatResponse(
        String name,
        long callCount,
        long totalTokens,
        double avgDurationMs
) {
}
