package top.egon.mario.investment.marketdata.repository.jdbc.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Latest contract quote payload persisted outside the JPA entity model.
 */
public record ContractQuoteWrite(
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
        Instant receivedAt
) {
    public ContractQuoteWrite {
        MarketDataModelValidation.ids(sourceId, instrumentId);
        MarketDataModelValidation.positive(lastPrice, "lastPrice");
        MarketDataModelValidation.optionalPositive(markPrice, "markPrice");
        MarketDataModelValidation.optionalPositive(indexPrice, "indexPrice");
        MarketDataModelValidation.optionalPositive(bidPrice, "bidPrice");
        MarketDataModelValidation.optionalPositive(askPrice, "askPrice");
        MarketDataModelValidation.optionalNonNegative(bidQuantity, "bidQuantity");
        MarketDataModelValidation.optionalNonNegative(askQuantity, "askQuantity");
        MarketDataModelValidation.optionalNonNegative(baseVolume24h, "baseVolume24h");
        MarketDataModelValidation.optionalNonNegative(quoteVolume24h, "quoteVolume24h");
        MarketDataModelValidation.optionalNonNegative(openInterest, "openInterest");
        Objects.requireNonNull(sourceTime, "sourceTime");
        Objects.requireNonNull(receivedAt, "receivedAt");
    }
}
