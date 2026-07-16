package top.egon.mario.investment.marketdata.web.dto;

import java.time.Instant;

/**
 * Shared platform-market summary used by the private overview aggregate.
 */
public record InvestmentMarketOverviewResponse(
        long subscribedInstrumentCount,
        long freshQuoteCount,
        long staleOrMissingQuoteCount,
        long openQualityIssueCount,
        Instant dataAsOf
) {
}
