package top.egon.mario.investment.research.report;

import java.util.Map;

/**
 * Stable JSON envelope retaining frozen inputs after generated metrics are persisted.
 */
public record PersistedResearchMetrics(
        FrozenResearchReportInput input,
        String inputHash,
        Object indicatorSnapshot,
        Map<String, Object> metrics
) {
}
