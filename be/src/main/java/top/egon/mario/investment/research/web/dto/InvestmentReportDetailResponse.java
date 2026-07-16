package top.egon.mario.investment.research.web.dto;

import java.util.List;

/**
 * Report content with evidence returned in the same owner-scoped query.
 */
public record InvestmentReportDetailResponse(
        InvestmentReportSummaryResponse report,
        String sourceType,
        String contentMarkdown,
        String metricsJson,
        List<InvestmentReportEvidenceResponse> evidence
) {

    public InvestmentReportDetailResponse {
        evidence = List.copyOf(evidence);
    }
}
