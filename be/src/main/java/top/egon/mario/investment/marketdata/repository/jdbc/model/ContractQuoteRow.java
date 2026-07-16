package top.egon.mario.investment.marketdata.repository.jdbc.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Latest persisted contract quote and its optimistic version.
 */
public record ContractQuoteRow(
        long sourceId,
        long instrumentId,
        BigDecimal lastPrice,
        BigDecimal markPrice,
        BigDecimal indexPrice,
        BigDecimal bidPrice,
        BigDecimal askPrice,
        BigDecimal bidQuantity,
        BigDecimal askQuantity,
        BigDecimal open24h,
        BigDecimal high24h,
        BigDecimal low24h,
        BigDecimal baseVolume24h,
        BigDecimal quoteVolume24h,
        BigDecimal change24h,
        BigDecimal fundingRate,
        Instant nextFundingTime,
        BigDecimal openInterest,
        Instant sourceTime,
        Instant receivedAt,
        long version
) {
}
