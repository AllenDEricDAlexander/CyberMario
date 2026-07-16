package top.egon.mario.investment.portfolio.margin;

import java.math.BigDecimal;

/**
 * One half-open maintenance-margin notional tier.
 */
public record PositionTier(
        int tierLevel,
        BigDecimal minimumNotional,
        BigDecimal maximumNotional,
        BigDecimal maximumLeverage,
        BigDecimal maintenanceMarginRate
) {

    public PositionTier {
        if (tierLevel <= 0) {
            throw new IllegalArgumentException("tierLevel must be positive");
        }
        requireNonNegative(minimumNotional, "minimumNotional");
        requirePositive(maximumNotional, "maximumNotional");
        if (maximumNotional.compareTo(minimumNotional) <= 0) {
            throw new IllegalArgumentException("maximumNotional must exceed minimumNotional");
        }
        requirePositive(maximumLeverage, "maximumLeverage");
        requireNonNegative(maintenanceMarginRate, "maintenanceMarginRate");
        if (maintenanceMarginRate.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("maintenanceMarginRate must be less than one");
        }
    }

    boolean contains(BigDecimal notional) {
        return notional.compareTo(minimumNotional) >= 0 && notional.compareTo(maximumNotional) < 0;
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireNonNegative(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
