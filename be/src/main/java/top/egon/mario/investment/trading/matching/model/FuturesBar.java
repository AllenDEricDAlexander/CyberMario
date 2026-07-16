package top.egon.mario.investment.trading.matching.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * One immutable market-price bar supplied to the matching kernel.
 */
public record FuturesBar(
        Instant openTime,
        Instant closeTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        boolean closed
) {

    public FuturesBar {
        Objects.requireNonNull(openTime, "openTime");
        Objects.requireNonNull(closeTime, "closeTime");
        if (!closeTime.isAfter(openTime)) {
            throw new IllegalArgumentException("closeTime must be after openTime");
        }
        requirePositive(open, "open");
        requirePositive(high, "high");
        requirePositive(low, "low");
        requirePositive(close, "close");
        if (low.compareTo(open) > 0 || low.compareTo(close) > 0
                || high.compareTo(open) < 0 || high.compareTo(close) < 0
                || low.compareTo(high) > 0) {
            throw new IllegalArgumentException("bar OHLC values are inconsistent");
        }
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
