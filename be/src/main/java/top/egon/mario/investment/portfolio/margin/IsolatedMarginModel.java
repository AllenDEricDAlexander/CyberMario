package top.egon.mario.investment.portfolio.margin;

import top.egon.mario.investment.common.model.PositionSide;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Arrays;
import java.util.Objects;

/**
 * Stateless USDT-linear isolated-margin formulas.
 */
public final class IsolatedMarginModel {

    public BigDecimal notional(BigDecimal quantity, BigDecimal markPrice, BigDecimal contractMultiplier) {
        return positive(quantity, "quantity")
                .multiply(positive(markPrice, "markPrice"))
                .multiply(positive(contractMultiplier, "contractMultiplier"))
                .abs();
    }

    public BigDecimal initialMargin(BigDecimal notional, BigDecimal leverage) {
        return nonNegative(notional, "notional")
                .divide(positive(leverage, "leverage"), MathContext.DECIMAL128);
    }

    public BigDecimal unrealizedPnl(PositionSide positionSide, BigDecimal entryPrice, BigDecimal markPrice,
                                    BigDecimal quantity, BigDecimal contractMultiplier) {
        Objects.requireNonNull(positionSide, "positionSide");
        BigDecimal priceChange = positionSide == PositionSide.LONG
                ? positive(markPrice, "markPrice").subtract(positive(entryPrice, "entryPrice"))
                : positive(entryPrice, "entryPrice").subtract(positive(markPrice, "markPrice"));
        return priceChange.multiply(positive(quantity, "quantity"))
                .multiply(positive(contractMultiplier, "contractMultiplier"));
    }

    public BigDecimal maintenanceMargin(BigDecimal notional, BigDecimal maintenanceMarginRate) {
        BigDecimal rate = nonNegative(maintenanceMarginRate, "maintenanceMarginRate");
        if (rate.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("maintenanceMarginRate must be less than one");
        }
        return nonNegative(notional, "notional").multiply(rate);
    }

    public BigDecimal maximumAllowedLeverage(BigDecimal contractMaximum, BigDecimal tierMaximum,
                                             BigDecimal accountMaximum, BigDecimal strategyMaximum) {
        return Arrays.stream(new BigDecimal[]{contractMaximum, tierMaximum, accountMaximum, strategyMaximum})
                .map(value -> positive(value, "leverage limit"))
                .min(BigDecimal::compareTo)
                .orElseThrow();
    }

    private static BigDecimal positive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static BigDecimal nonNegative(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }
}
