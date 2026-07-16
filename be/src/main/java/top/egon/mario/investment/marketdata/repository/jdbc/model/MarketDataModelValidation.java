package top.egon.mario.investment.marketdata.repository.jdbc.model;

import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

final class MarketDataModelValidation {

    private MarketDataModelValidation() {
    }

    static void ids(long sourceId, long instrumentId) {
        if (sourceId <= 0 || instrumentId <= 0) {
            throw new IllegalArgumentException("sourceId and instrumentId must be positive");
        }
    }

    static void bar(Instant openTime, Instant closeTime, BigDecimal open, BigDecimal high,
                    BigDecimal low, BigDecimal close, BigDecimal baseVolume, BigDecimal quoteVolume) {
        Objects.requireNonNull(openTime, "openTime");
        Objects.requireNonNull(closeTime, "closeTime");
        if (!closeTime.isAfter(openTime)) {
            throw new IllegalArgumentException("closeTime must be after openTime");
        }
        ohlcv(open, high, low, close, baseVolume, quoteVolume);
    }

    static void ohlcv(BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
                      BigDecimal baseVolume, BigDecimal quoteVolume) {
        positive(open, "openPrice");
        positive(high, "highPrice");
        positive(low, "lowPrice");
        positive(close, "closePrice");
        optionalNonNegative(Objects.requireNonNull(baseVolume, "baseVolume"), "baseVolume");
        optionalNonNegative(Objects.requireNonNull(quoteVolume, "quoteVolume"), "quoteVolume");
        if (low.compareTo(open) > 0 || low.compareTo(close) > 0 || low.compareTo(high) > 0
                || high.compareTo(open) < 0 || high.compareTo(close) < 0) {
            throw new IllegalArgumentException("OHLC bounds are inconsistent");
        }
    }

    static void positive(BigDecimal value, String field) {
        Objects.requireNonNull(value, field);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    static void optionalPositive(BigDecimal value, String field) {
        if (value != null && value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive when present");
        }
    }

    static void optionalNonNegative(BigDecimal value, String field) {
        if (value != null && value.signum() < 0) {
            throw new IllegalArgumentException(field + " must be non-negative when present");
        }
    }

    static String checksum(String checksum) {
        if (!StringUtils.hasText(checksum) || checksum.length() > 128) {
            throw new IllegalArgumentException("checksum must contain 1 to 128 characters");
        }
        return checksum;
    }
}
