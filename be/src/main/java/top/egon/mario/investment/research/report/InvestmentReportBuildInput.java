package top.egon.mario.investment.research.report;

/**
 * Minimal immutable payload stored by the durable REPORT_BUILD job.
 */
public record InvestmentReportBuildInput(long reportId, long reportVersion) {
}
