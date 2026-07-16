package top.egon.mario.investment.marketdata.provider.model;

import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.common.model.ProductType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Normalized OHLCV candle emitted by an external adapter.
 */
public record ExternalCandle(
        String sourceCode,
        ProductType productType,
        String symbol,
        PriceType priceType,
        BarInterval interval,
        Instant openTime,
        Instant closeTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal baseVolume,
        BigDecimal quoteVolume,
        boolean closed,
        Instant observedAt
) {
    public ExternalCandle {
        sourceCode = ProviderModelValidation.sourceCode(sourceCode);
        Objects.requireNonNull(productType, "productType");
        symbol = ProviderModelValidation.symbol(symbol);
        Objects.requireNonNull(priceType, "priceType");
        Objects.requireNonNull(interval, "interval");
        if (priceType == PriceType.NONE || interval == BarInterval.NONE) {
            throw new IllegalArgumentException("Concrete candle price type and interval are required");
        }
        ProviderModelValidation.timeWindow(openTime, closeTime);
        ProviderModelValidation.positive(open, "open");
        ProviderModelValidation.positive(high, "high");
        ProviderModelValidation.positive(low, "low");
        ProviderModelValidation.positive(close, "close");
        ProviderModelValidation.nonNegative(baseVolume, "baseVolume");
        ProviderModelValidation.nonNegative(quoteVolume, "quoteVolume");
        if (high.compareTo(open) < 0 || high.compareTo(close) < 0 || high.compareTo(low) < 0
                || low.compareTo(open) > 0 || low.compareTo(close) > 0) {
            throw new IllegalArgumentException("Candle high/low bounds are inconsistent with open/close");
        }
        ProviderModelValidation.instant(observedAt, "observedAt");
    }
}
