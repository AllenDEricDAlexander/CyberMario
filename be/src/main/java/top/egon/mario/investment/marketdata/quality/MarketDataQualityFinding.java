package top.egon.mario.investment.marketdata.quality;

import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Provider-independent quality finding before it becomes an audited database fact.
 */
public record MarketDataQualityFinding(
        MarketDataQualityCode code,
        String severity,
        String dataType,
        PriceType priceType,
        BarInterval interval,
        Instant pointTime,
        Map<String, Object> details
) {
    public MarketDataQualityFinding {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(dataType, "dataType");
        priceType = priceType == null ? PriceType.NONE : priceType;
        interval = interval == null ? BarInterval.NONE : interval;
        Objects.requireNonNull(pointTime, "pointTime");
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
