package top.egon.mario.agent.model.dto.response;

/**
 * Top-line model audit metrics for the selected dashboard range.
 */
public record ModelAuditOverviewResponse(
        long callCount,
        long successCount,
        long failedCount,
        double successRate,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        long promptChars,
        long completionChars,
        double avgDurationMs,
        long streamingCount
) {
}
