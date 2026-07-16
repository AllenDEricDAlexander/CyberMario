package top.egon.mario.investment.marketdata.repository.jdbc.model;

import top.egon.mario.investment.common.model.PriceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Normalized daily bar ready for revision-aware persistence.
 */
public record MarketBarDailyWrite(
        long sourceId,
        long instrumentId,
        PriceType priceType,
        LocalDate barDate,
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
    public MarketBarDailyWrite {
        MarketDataModelValidation.ids(sourceId, instrumentId);
        Objects.requireNonNull(priceType, "priceType");
        if (priceType == PriceType.NONE) {
            throw new IllegalArgumentException("Concrete priceType is required");
        }
        Objects.requireNonNull(barDate, "barDate");
        MarketDataModelValidation.ohlcv(openPrice, highPrice, lowPrice, closePrice, baseVolume, quoteVolume);
        Objects.requireNonNull(ingestedAt, "ingestedAt");
        checksum = MarketDataModelValidation.checksum(checksum);
    }
}
