package top.egon.mario.investment.portfolio.margin;

import top.egon.mario.investment.common.model.PositionSide;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Minimum position state required for isolated liquidation checks.
 */
public record IsolatedPosition(
        PositionSide positionSide,
        BigDecimal quantity,
        BigDecimal entryPrice,
        BigDecimal positionMargin
) {

    public IsolatedPosition {
        Objects.requireNonNull(positionSide, "positionSide");
        requirePositive(quantity, "quantity");
        requirePositive(entryPrice, "entryPrice");
        if (positionMargin == null || positionMargin.signum() < 0) {
            throw new IllegalArgumentException("positionMargin must not be negative");
        }
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
