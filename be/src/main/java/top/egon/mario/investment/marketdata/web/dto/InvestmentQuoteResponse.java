package top.egon.mario.investment.marketdata.web.dto;

import java.time.Instant;

/**
 * Latest quote values serialized as lossless decimal strings.
 */
public record InvestmentQuoteResponse(
        Long instrumentId,
        String lastPrice,
        String markPrice,
        String indexPrice,
        String bidPrice,
        String askPrice,
        String bidQuantity,
        String askQuantity,
        String open24h,
        String high24h,
        String low24h,
        String baseVolume24h,
        String quoteVolume24h,
        String change24h,
        String fundingRate,
        Instant nextFundingTime,
        String openInterest,
        Instant sourceTime,
        Instant receivedAt,
        long version,
        Instant dataAsOf,
        InvestmentFreshnessResponse freshness
) {
}
