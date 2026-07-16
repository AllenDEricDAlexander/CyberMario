package top.egon.mario.investment.marketdata.provider.model;

import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.common.model.ProductType;

import java.time.Instant;
import java.util.Objects;

/**
 * Provider-neutral candle query with an explicit half-open UTC time range.
 */
public record CandleQuery(
        ProductType productType,
        String symbol,
        PriceType priceType,
        BarInterval interval,
        Instant startInclusive,
        Instant endExclusive,
        int limit
) {
    public CandleQuery {
        Objects.requireNonNull(productType, "productType");
        symbol = ProviderModelValidation.symbol(symbol);
        Objects.requireNonNull(priceType, "priceType");
        Objects.requireNonNull(interval, "interval");
        if (priceType == PriceType.NONE || interval == BarInterval.NONE) {
            throw new IllegalArgumentException("Concrete candle price type and interval are required");
        }
        ProviderModelValidation.timeWindow(startInclusive, endExclusive);
        ProviderModelValidation.limit(limit);
    }
}
