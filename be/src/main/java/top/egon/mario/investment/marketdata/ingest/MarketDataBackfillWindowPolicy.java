package top.egon.mario.investment.marketdata.ingest;

import top.egon.mario.investment.common.model.BarInterval;

import java.time.Duration;

/**
 * Keeps one scheduled candle backfill job bounded while preserving the configured retention range.
 */
public final class MarketDataBackfillWindowPolicy {

    private static final int MAX_PAGES_PER_JOB = 100;
    private static final Duration RECENT_MINUTE_WINDOW = Duration.ofDays(1);

    private MarketDataBackfillWindowPolicy() {
    }

    public static Duration maximumJobWindow(BarInterval interval, int pageSize) {
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be positive");
        }
        return intervalDuration(interval).multipliedBy(Math.multiplyExact(pageSize, MAX_PAGES_PER_JOB));
    }

    public static Duration initialJobWindow(BarInterval interval, int pageSize) {
        Duration maximum = maximumJobWindow(interval, pageSize);
        return interval == BarInterval.M1 && RECENT_MINUTE_WINDOW.compareTo(maximum) < 0
                ? RECENT_MINUTE_WINDOW : maximum;
    }

    private static Duration intervalDuration(BarInterval interval) {
        return switch (interval) {
            case M1 -> Duration.ofMinutes(1);
            case M5 -> Duration.ofMinutes(5);
            case M15 -> Duration.ofMinutes(15);
            case M30 -> Duration.ofMinutes(30);
            case H1 -> Duration.ofHours(1);
            case H4 -> Duration.ofHours(4);
            case D1 -> Duration.ofDays(1);
            case NONE -> throw new IllegalArgumentException("Concrete interval is required");
        };
    }
}
