package top.egon.mario.investment.marketdata.subscription;

import top.egon.mario.investment.common.model.BarInterval;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Per-interval retention declared in code; permanent intervals have no duration.
 */
public record RetentionPolicy(
        Set<BarInterval> permanentIntervals,
        Map<BarInterval, Duration> retainedFor
) {
    public RetentionPolicy {
        Objects.requireNonNull(permanentIntervals, "permanentIntervals");
        Objects.requireNonNull(retainedFor, "retainedFor");
        if (permanentIntervals.stream().anyMatch(Objects::isNull)
                || retainedFor.entrySet().stream()
                .anyMatch(entry -> entry.getKey() == null || entry.getValue() == null)) {
            throw new IllegalArgumentException("Retention policy must not contain null entries");
        }
        if (permanentIntervals.contains(BarInterval.NONE) || retainedFor.containsKey(BarInterval.NONE)) {
            throw new IllegalArgumentException("Retention policy requires concrete intervals");
        }
        permanentIntervals = Set.copyOf(permanentIntervals);
        retainedFor.forEach((interval, duration) -> {
            if (duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException("Retention duration must be positive: " + interval);
            }
        });
        retainedFor = Map.copyOf(retainedFor);
    }
}
