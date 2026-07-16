package top.egon.mario.investment.marketdata.web.dto;

import java.time.Instant;

/**
 * Explicit freshness state for a market-data projection.
 */
public record InvestmentFreshnessResponse(
        String status,
        Instant observedAt,
        long ageSeconds
) {
}
