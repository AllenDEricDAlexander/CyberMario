package top.egon.mario.investment.research.web.dto;

import java.time.Instant;

/**
 * Page row for a frozen research-report version.
 */
public record InvestmentReportSummaryResponse(
        Long reportId,
        Long workspaceId,
        Long instrumentId,
        String reportType,
        String title,
        String summary,
        String status,
        Long reportVersion,
        Instant dataAsOf,
        Instant createdAt
) {
}
