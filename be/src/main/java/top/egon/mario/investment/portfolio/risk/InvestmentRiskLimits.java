package top.egon.mario.investment.portfolio.risk;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable, unit-explicit risk ceilings. Caller limits can only tighten account limits.
 */
public record InvestmentRiskLimits(
        BigDecimal maxLeverage,
        BigDecimal maxOrderNotional,
        BigDecimal maxPositionNotional,
        BigDecimal maxGrossExposureNotional,
        Long maxOpenPositions,
        BigDecimal maxDailyLossAmount,
        BigDecimal maxDrawdownRatio,
        Long maxOrdersPerHour,
        Long cooldownSeconds,
        Long maxMarketDataAgeSeconds,
        BigDecimal maxSlippageBps
) {
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal TEN_THOUSAND = new BigDecimal("10000");

    public InvestmentRiskLimits {
        positive(maxLeverage, "maxLeverage");
        positive(maxOrderNotional, "maxOrderNotional");
        positive(maxPositionNotional, "maxPositionNotional");
        positive(maxGrossExposureNotional, "maxGrossExposureNotional");
        positive(maxOpenPositions, "maxOpenPositions");
        positive(maxDailyLossAmount, "maxDailyLossAmount");
        positive(maxDrawdownRatio, "maxDrawdownRatio");
        if (maxDrawdownRatio.compareTo(ONE) > 0) {
            throw new IllegalArgumentException("maxDrawdownRatio must not exceed one");
        }
        positive(maxOrdersPerHour, "maxOrdersPerHour");
        nonNegative(cooldownSeconds, "cooldownSeconds");
        positive(maxMarketDataAgeSeconds, "maxMarketDataAgeSeconds");
        nonNegative(maxSlippageBps, "maxSlippageBps");
        if (maxSlippageBps.compareTo(TEN_THOUSAND) > 0) {
            throw new IllegalArgumentException("maxSlippageBps must not exceed 10000");
        }
        if (maxOrderNotional.compareTo(maxPositionNotional) > 0
                || maxPositionNotional.compareTo(maxGrossExposureNotional) > 0) {
            throw new IllegalArgumentException("notional limits must be ordered from order to gross exposure");
        }
    }

    /**
     * Applies code strategy or Agent ceilings without permitting any account limit to widen.
     */
    public InvestmentRiskLimits tightenedBy(InvestmentRiskLimits requested) {
        if (requested == null) {
            return this;
        }
        return new InvestmentRiskLimits(
                min(maxLeverage, requested.maxLeverage),
                min(maxOrderNotional, requested.maxOrderNotional),
                min(maxPositionNotional, requested.maxPositionNotional),
                min(maxGrossExposureNotional, requested.maxGrossExposureNotional),
                Math.min(maxOpenPositions, requested.maxOpenPositions),
                min(maxDailyLossAmount, requested.maxDailyLossAmount),
                min(maxDrawdownRatio, requested.maxDrawdownRatio),
                Math.min(maxOrdersPerHour, requested.maxOrdersPerHour),
                Math.max(cooldownSeconds, requested.cooldownSeconds),
                Math.min(maxMarketDataAgeSeconds, requested.maxMarketDataAgeSeconds),
                min(maxSlippageBps, requested.maxSlippageBps));
    }

    private static BigDecimal min(BigDecimal left, BigDecimal right) {
        return left.min(right);
    }

    private static void positive(BigDecimal value, String field) {
        Objects.requireNonNull(value, field);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private static void nonNegative(BigDecimal value, String field) {
        Objects.requireNonNull(value, field);
        if (value.signum() < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
    }

    private static void positive(Long value, String field) {
        Objects.requireNonNull(value, field);
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private static void nonNegative(Long value, String field) {
        Objects.requireNonNull(value, field);
        if (value < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
    }
}
