package top.egon.mario.investment.marketdata.provider.model;

import top.egon.mario.investment.common.model.ContractType;
import top.egon.mario.investment.common.model.ProductType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Normalized contract metadata emitted by an external adapter.
 */
public record ExternalContract(
        String sourceCode,
        ProductType productType,
        String symbol,
        ContractType contractType,
        String baseAsset,
        String quoteAsset,
        String settleAsset,
        BigDecimal contractSize,
        BigDecimal minimumOrderQuantity,
        BigDecimal quantityStep,
        BigDecimal priceTick,
        int maximumLeverage,
        Instant observedAt
) {
    public ExternalContract {
        sourceCode = ProviderModelValidation.sourceCode(sourceCode);
        Objects.requireNonNull(productType, "productType");
        symbol = ProviderModelValidation.symbol(symbol);
        Objects.requireNonNull(contractType, "contractType");
        baseAsset = ProviderModelValidation.asset(baseAsset, "baseAsset");
        quoteAsset = ProviderModelValidation.asset(quoteAsset, "quoteAsset");
        settleAsset = ProviderModelValidation.asset(settleAsset, "settleAsset");
        ProviderModelValidation.positive(contractSize, "contractSize");
        ProviderModelValidation.positive(minimumOrderQuantity, "minimumOrderQuantity");
        ProviderModelValidation.positive(quantityStep, "quantityStep");
        ProviderModelValidation.positive(priceTick, "priceTick");
        if (maximumLeverage < 1) {
            throw new IllegalArgumentException("maximumLeverage must be positive");
        }
        ProviderModelValidation.instant(observedAt, "observedAt");
    }
}
