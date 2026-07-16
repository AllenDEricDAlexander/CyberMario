package top.egon.mario.investment.marketdata.provider.model;

import top.egon.mario.investment.common.model.ProductType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Normalized latest price snapshot emitted by an external adapter.
 */
public record ExternalContractTicker(
        String sourceCode,
        ProductType productType,
        String symbol,
        BigDecimal lastPrice,
        BigDecimal markPrice,
        BigDecimal indexPrice,
        BigDecimal bidPrice,
        BigDecimal askPrice,
        BigDecimal openInterest,
        Instant observedAt
) {
    public ExternalContractTicker {
        sourceCode = ProviderModelValidation.sourceCode(sourceCode);
        Objects.requireNonNull(productType, "productType");
        symbol = ProviderModelValidation.symbol(symbol);
        ProviderModelValidation.positive(lastPrice, "lastPrice");
        if (markPrice != null) {
            ProviderModelValidation.positive(markPrice, "markPrice");
        }
        if (indexPrice != null) {
            ProviderModelValidation.positive(indexPrice, "indexPrice");
        }
        if (bidPrice != null) {
            ProviderModelValidation.positive(bidPrice, "bidPrice");
        }
        if (askPrice != null) {
            ProviderModelValidation.positive(askPrice, "askPrice");
        }
        if (openInterest != null) {
            ProviderModelValidation.nonNegative(openInterest, "openInterest");
        }
        if (bidPrice != null && askPrice != null && bidPrice.compareTo(askPrice) > 0) {
            throw new IllegalArgumentException("bidPrice must not exceed askPrice");
        }
        ProviderModelValidation.instant(observedAt, "observedAt");
    }
}
