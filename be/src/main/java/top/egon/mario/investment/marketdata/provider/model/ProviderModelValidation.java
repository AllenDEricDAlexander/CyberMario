package top.egon.mario.investment.marketdata.provider.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

final class ProviderModelValidation {

    private static final Pattern SOURCE_CODE = Pattern.compile("[A-Z0-9][A-Z0-9_.:-]*");
    private static final Pattern SYMBOL = Pattern.compile("[A-Z0-9][A-Z0-9_-]*");
    private static final Pattern ASSET = Pattern.compile("[A-Z0-9][A-Z0-9_-]*");

    private ProviderModelValidation() {
    }

    static String sourceCode(String value) {
        return normalized(value, SOURCE_CODE, "sourceCode");
    }

    static String symbol(String value) {
        return normalized(value, SYMBOL, "symbol");
    }

    static String asset(String value, String name) {
        return normalized(value, ASSET, name);
    }

    static BigDecimal positive(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    static BigDecimal nonNegative(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        if (value.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    static Instant instant(Instant value, String name) {
        return Objects.requireNonNull(value, name);
    }

    static void timeWindow(Instant startInclusive, Instant endExclusive) {
        instant(startInclusive, "startInclusive");
        instant(endExclusive, "endExclusive");
        if (!startInclusive.isBefore(endExclusive)) {
            throw new IllegalArgumentException("startInclusive must be before endExclusive");
        }
    }

    static int limit(int value) {
        if (value < 1 || value > 1000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
        return value;
    }

    private static String normalized(String value, Pattern pattern, String name) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a normalized identifier");
        }
        return value;
    }
}
