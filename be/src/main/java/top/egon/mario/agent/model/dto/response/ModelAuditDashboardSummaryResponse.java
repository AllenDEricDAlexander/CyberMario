package top.egon.mario.agent.model.dto.response;

import top.egon.mario.agent.model.dto.enums.ModelAuditDashboardScope;

import java.time.Instant;
import java.util.List;

/**
 * Summary payload for model audit dashboard metrics and charts.
 */
public record ModelAuditDashboardSummaryResponse(
        ModelAuditDashboardScope scope,
        Instant startAt,
        Instant endAt,
        ModelAuditOverviewResponse overview,
        List<ModelAuditTrendPointResponse> tokenTrend,
        List<ModelAuditTrendPointResponse> callTrend,
        List<ModelAuditDimensionStatResponse> providerStats,
        List<ModelAuditDimensionStatResponse> modelStats,
        List<ModelAuditDimensionStatResponse> scenarioStats,
        List<ModelAuditDimensionStatResponse> statusStats,
        List<ModelAuditUserStatResponse> userStats
) {

    public ModelAuditDashboardSummaryResponse {
        tokenTrend = tokenTrend == null ? List.of() : List.copyOf(tokenTrend);
        callTrend = callTrend == null ? List.of() : List.copyOf(callTrend);
        providerStats = providerStats == null ? List.of() : List.copyOf(providerStats);
        modelStats = modelStats == null ? List.of() : List.copyOf(modelStats);
        scenarioStats = scenarioStats == null ? List.of() : List.copyOf(scenarioStats);
        statusStats = statusStats == null ? List.of() : List.copyOf(statusStats);
        userStats = userStats == null ? List.of() : List.copyOf(userStats);
    }

}
