package top.egon.mario.investment.marketdata.provider.model;

import top.egon.mario.investment.common.model.ProductType;

import java.time.Instant;
import java.util.Objects;

/**
 * Provider-neutral funding-rate query with an explicit half-open UTC time range.
 */
public record FundingRateQuery(
        ProductType productType,
        String symbol,
        Instant startInclusive,
        Instant endExclusive,
        int limit
) {
    public FundingRateQuery {
        Objects.requireNonNull(productType, "productType");
        symbol = ProviderModelValidation.symbol(symbol);
        ProviderModelValidation.timeWindow(startInclusive, endExclusive);
        ProviderModelValidation.limit(limit);
    }
}
