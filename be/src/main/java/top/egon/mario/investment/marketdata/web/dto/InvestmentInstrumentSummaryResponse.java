package top.egon.mario.investment.marketdata.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * Stable public market-list projection keyed only by the internal instrument id.
 */
public record InvestmentInstrumentSummaryResponse(
        Long instrumentId,
        String venueCode,
        String symbol,
        String baseAsset,
        String quoteAsset,
        String status,
        String lastPrice,
        String markPrice,
        String change24h,
        Instant dataAsOf,
        InvestmentFreshnessResponse freshness,
        List<String> availableCapabilities
) {
}
