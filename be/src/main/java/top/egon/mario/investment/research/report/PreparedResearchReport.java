package top.egon.mario.investment.research.report;

/**
 * Detached report state used while the slow generator runs outside a database transaction.
 */
public record PreparedResearchReport(ResearchReportGenerationContext context, String status) {

    public boolean ready() {
        return "READY".equals(status);
    }
}
