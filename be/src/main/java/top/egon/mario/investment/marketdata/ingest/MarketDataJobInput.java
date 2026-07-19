package top.egon.mario.investment.marketdata.ingest;

import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.common.model.ProductType;

import java.time.Instant;
import java.util.Objects;

/**
 * Durable, provider-neutral input for one market-data ingestion dimension.
 */
public record MarketDataJobInput(
        String sourceCode,
        ProductType productType,
        String symbol,
        DataCapability capability,
        PriceType priceType,
        BarInterval interval,
        Instant startInclusive,
        Instant endExclusive,
        int pageSize,
        String triggerSource
) {
    public MarketDataJobInput(String sourceCode, ProductType productType, String symbol,
                              DataCapability capability, PriceType priceType, BarInterval interval,
                              Instant startInclusive, Instant endExclusive, int pageSize) {
        this(sourceCode, productType, symbol, capability, priceType, interval, startInclusive, endExclusive,
                pageSize, "SCHEDULED");
    }

    public MarketDataJobInput {
        Objects.requireNonNull(sourceCode, "sourceCode");
        Objects.requireNonNull(productType, "productType");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(capability, "capability");
        priceType = priceType == null ? PriceType.NONE : priceType;
        interval = interval == null ? BarInterval.NONE : interval;
        if ((startInclusive == null) != (endExclusive == null)
                || startInclusive != null && !startInclusive.isBefore(endExclusive)) {
            throw new IllegalArgumentException("A valid half-open time range is required");
        }
        if (pageSize < 1 || pageSize > 1000) {
            throw new IllegalArgumentException("pageSize must be between 1 and 1000");
        }
        triggerSource = triggerSource == null ? "SCHEDULED" : triggerSource;
        if (!triggerSource.equals("SCHEDULED") && !triggerSource.equals("MANUAL")) {
            throw new IllegalArgumentException("triggerSource must be SCHEDULED or MANUAL");
        }
    }

    public MarketDataJobInput withRange(Instant start, Instant end) {
        return new MarketDataJobInput(sourceCode, productType, symbol, capability, priceType, interval,
                start, end, pageSize, triggerSource);
    }
}
