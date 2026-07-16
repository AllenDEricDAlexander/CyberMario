package top.egon.mario.investment.marketdata.subscription;

import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.common.model.ProductType;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Immutable code declaration of an allowed source, symbol and data shape.
 */
public record MarketSubscription(
        String sourceCode,
        ProductType productType,
        String symbol,
        Set<BarInterval> intervals,
        Set<PriceType> priceTypes,
        Set<DataCapability> capabilities,
        SubscriptionSchedule schedule,
        RetentionPolicy retentionPolicy
) {
    private static final Pattern SOURCE_CODE = Pattern.compile("[A-Z0-9][A-Z0-9_.:-]*");
    private static final Pattern SYMBOL = Pattern.compile("[A-Z0-9][A-Z0-9_-]*");

    public MarketSubscription {
        if (sourceCode == null || !SOURCE_CODE.matcher(sourceCode).matches()) {
            throw new IllegalArgumentException("sourceCode must be a normalized identifier");
        }
        Objects.requireNonNull(productType, "productType");
        if (symbol == null || !SYMBOL.matcher(symbol).matches()) {
            throw new IllegalArgumentException("symbol must be a normalized identifier");
        }
        intervals = immutableSet(intervals, "intervals");
        priceTypes = immutableSet(priceTypes, "priceTypes");
        capabilities = immutableSet(capabilities, "capabilities");
        if (intervals.contains(BarInterval.NONE) || priceTypes.contains(PriceType.NONE)) {
            throw new IllegalArgumentException("Subscription requires concrete interval and price values");
        }
        Objects.requireNonNull(schedule, "schedule");
        Objects.requireNonNull(retentionPolicy, "retentionPolicy");
    }

    private static <T> Set<T> immutableSet(Set<T> values, String name) {
        Objects.requireNonNull(values, name);
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(name + " must not contain null entries");
        }
        return Set.copyOf(values);
    }
}
