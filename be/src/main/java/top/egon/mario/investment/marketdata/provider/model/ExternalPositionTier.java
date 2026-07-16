package top.egon.mario.investment.marketdata.provider.model;

import top.egon.mario.investment.common.model.ProductType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Normalized position tier emitted by an external adapter.
 */
public record ExternalPositionTier(
        String sourceCode,
        ProductType productType,
        String symbol,
        int tier,
        BigDecimal minimumNotional,
        BigDecimal maximumNotional,
        BigDecimal maintenanceMarginRatio,
        int maximumLeverage,
        Instant observedAt
) {
    public ExternalPositionTier {
        sourceCode = ProviderModelValidation.sourceCode(sourceCode);
        Objects.requireNonNull(productType, "productType");
        symbol = ProviderModelValidation.symbol(symbol);
        if (tier < 1) {
            throw new IllegalArgumentException("tier must be positive");
        }
        ProviderModelValidation.nonNegative(minimumNotional, "minimumNotional");
        ProviderModelValidation.positive(maximumNotional, "maximumNotional");
        ProviderModelValidation.nonNegative(maintenanceMarginRatio, "maintenanceMarginRatio");
        if (minimumNotional.compareTo(maximumNotional) >= 0) {
            throw new IllegalArgumentException("minimumNotional must be less than maximumNotional");
        }
        if (maximumLeverage < 1) {
            throw new IllegalArgumentException("maximumLeverage must be positive");
        }
        ProviderModelValidation.instant(observedAt, "observedAt");
    }
}
