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
        ProviderModelValidation.positive(markPrice, "markPrice");
        ProviderModelValidation.positive(indexPrice, "indexPrice");
        ProviderModelValidation.positive(bidPrice, "bidPrice");
        ProviderModelValidation.positive(askPrice, "askPrice");
        ProviderModelValidation.nonNegative(openInterest, "openInterest");
        if (bidPrice.compareTo(askPrice) > 0) {
            throw new IllegalArgumentException("bidPrice must not exceed askPrice");
        }
        ProviderModelValidation.instant(observedAt, "observedAt");
    }
}
