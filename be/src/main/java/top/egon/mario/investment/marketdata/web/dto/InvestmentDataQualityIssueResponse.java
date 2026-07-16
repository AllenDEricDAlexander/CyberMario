package top.egon.mario.investment.marketdata.web.dto;

import java.time.Instant;

/**
 * Platform quality issue without provider credentials or raw request data.
 */
public record InvestmentDataQualityIssueResponse(
        Long id,
        Long instrumentId,
        String dataType,
        String priceType,
        String interval,
        Instant pointTime,
        String issueCode,
        String severity,
        String resolutionStatus,
        Instant resolvedAt,
        Instant createdAt
) {
}
