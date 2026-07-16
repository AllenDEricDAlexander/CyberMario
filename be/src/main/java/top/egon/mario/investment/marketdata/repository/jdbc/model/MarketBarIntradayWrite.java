package top.egon.mario.investment.marketdata.repository.jdbc.model;

import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Normalized intraday bar ready for revision-aware persistence.
 */
public record MarketBarIntradayWrite(
        long sourceId,
        long instrumentId,
        PriceType priceType,
        BarInterval interval,
        Instant openTime,
        Instant closeTime,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal closePrice,
        BigDecimal baseVolume,
        BigDecimal quoteVolume,
        boolean closed,
        Instant sourceUpdatedAt,
        Instant ingestedAt,
        String checksum
) {
    public MarketBarIntradayWrite {
        MarketDataModelValidation.ids(sourceId, instrumentId);
        Objects.requireNonNull(priceType, "priceType");
        Objects.requireNonNull(interval, "interval");
        if (priceType == PriceType.NONE || interval == BarInterval.NONE) {
            throw new IllegalArgumentException("Concrete priceType and interval are required");
        }
        MarketDataModelValidation.bar(openTime, closeTime, openPrice, highPrice, lowPrice, closePrice,
                baseVolume, quoteVolume);
        Objects.requireNonNull(ingestedAt, "ingestedAt");
        checksum = MarketDataModelValidation.checksum(checksum);
    }
}
