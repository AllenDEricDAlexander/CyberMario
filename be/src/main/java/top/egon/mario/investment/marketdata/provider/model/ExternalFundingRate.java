package top.egon.mario.investment.marketdata.provider.model;

import top.egon.mario.investment.common.model.ProductType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Normalized funding-rate observation emitted by an external adapter.
 */
public record ExternalFundingRate(
        String sourceCode,
        ProductType productType,
        String symbol,
        BigDecimal rate,
        Instant fundingTime,
        Instant observedAt
) {
    public ExternalFundingRate {
        sourceCode = ProviderModelValidation.sourceCode(sourceCode);
        Objects.requireNonNull(productType, "productType");
        symbol = ProviderModelValidation.symbol(symbol);
        Objects.requireNonNull(rate, "rate");
        ProviderModelValidation.instant(fundingTime, "fundingTime");
        ProviderModelValidation.instant(observedAt, "observedAt");
    }
}
