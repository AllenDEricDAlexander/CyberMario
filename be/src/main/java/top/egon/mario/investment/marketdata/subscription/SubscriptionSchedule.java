package top.egon.mario.investment.marketdata.subscription;

import top.egon.mario.investment.common.model.DataCapability;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Per-capability refresh and optional backfill windows declared in code.
 */
public record SubscriptionSchedule(
        Map<DataCapability, Duration> refreshIntervals,
        Map<DataCapability, Duration> backfillWindows
) {
    public SubscriptionSchedule {
        refreshIntervals = immutableDurations(refreshIntervals, "refreshIntervals");
        backfillWindows = immutableDurations(backfillWindows, "backfillWindows");
    }

    private static Map<DataCapability, Duration> immutableDurations(Map<DataCapability, Duration> values,
                                                                    String name) {
        Objects.requireNonNull(values, name);
        if (values.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getValue() == null)) {
            throw new IllegalArgumentException(name + " must not contain null entries");
        }
        values.forEach((capability, duration) -> {
            if (duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException(name + " durations must be positive: " + capability);
            }
        });
        return Map.copyOf(values);
    }
}
