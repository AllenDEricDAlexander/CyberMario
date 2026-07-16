package top.egon.mario.investment.research.web.dto;

/**
 * Immediate queue acknowledgement for asynchronous report creation.
 */
public record CreateInvestmentReportResponse(InvestmentReportSummaryResponse report, long jobId) {
}
