package top.egon.mario.investment.research.report;

/**
 * Immutable generation context reconstructed from the persisted report version.
 */
public record ResearchReportGenerationContext(
        long reportId,
        long workspaceId,
        long reportVersion,
        FrozenResearchReportInput input,
        String inputHash
) {
}
