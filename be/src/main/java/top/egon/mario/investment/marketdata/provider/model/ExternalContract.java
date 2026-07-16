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
        Integer pricePrecision,
        Integer quantityPrecision,
        BigDecimal priceEndStep,
        BigDecimal quantityStep,
        BigDecimal contractMultiplier,
        BigDecimal minTradeQuantity,
        BigDecimal minTradeNotional,
        BigDecimal maxMarketOrderQuantity,
        BigDecimal maxLimitOrderQuantity,
        BigDecimal makerFeeRate,
        BigDecimal takerFeeRate,
        BigDecimal minLeverage,
        BigDecimal maxLeverage,
        Integer fundingIntervalHours,
        BigDecimal buyLimitPriceRatio,
        BigDecimal sellLimitPriceRatio,
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
        Objects.requireNonNull(pricePrecision, "pricePrecision");
        Objects.requireNonNull(quantityPrecision, "quantityPrecision");
        if (pricePrecision < 0) {
            throw new IllegalArgumentException("pricePrecision must not be negative");
        }
        if (quantityPrecision < 0) {
            throw new IllegalArgumentException("quantityPrecision must not be negative");
        }
        ProviderModelValidation.positive(priceEndStep, "priceEndStep");
        ProviderModelValidation.positive(quantityStep, "quantityStep");
        ProviderModelValidation.positive(contractMultiplier, "contractMultiplier");
        ProviderModelValidation.positive(minTradeQuantity, "minTradeQuantity");
        ProviderModelValidation.positive(minTradeNotional, "minTradeNotional");
        ProviderModelValidation.positive(maxMarketOrderQuantity, "maxMarketOrderQuantity");
        ProviderModelValidation.positive(maxLimitOrderQuantity, "maxLimitOrderQuantity");
        ProviderModelValidation.nonNegative(makerFeeRate, "makerFeeRate");
        ProviderModelValidation.nonNegative(takerFeeRate, "takerFeeRate");
        ProviderModelValidation.positive(minLeverage, "minLeverage");
        ProviderModelValidation.positive(maxLeverage, "maxLeverage");
        if (maxLeverage.compareTo(minLeverage) < 0) {
            throw new IllegalArgumentException("maxLeverage must not be less than minLeverage");
        }
        Objects.requireNonNull(fundingIntervalHours, "fundingIntervalHours");
        if (fundingIntervalHours < 1) {
            throw new IllegalArgumentException("fundingIntervalHours must be positive");
        }
        ProviderModelValidation.positive(buyLimitPriceRatio, "buyLimitPriceRatio");
        ProviderModelValidation.positive(sellLimitPriceRatio, "sellLimitPriceRatio");
        ProviderModelValidation.instant(observedAt, "observedAt");
    }
}
