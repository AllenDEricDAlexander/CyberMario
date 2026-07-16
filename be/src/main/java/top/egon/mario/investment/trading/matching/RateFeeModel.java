package top.egon.mario.investment.trading.matching;

import top.egon.mario.investment.trading.matching.model.LiquidityRole;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Maker/taker fee schedule frozen into a simulation input snapshot.
 */
public final class RateFeeModel implements FeeModel {

    private final BigDecimal makerFeeRate;
    private final BigDecimal takerFeeRate;

    public RateFeeModel(BigDecimal makerFeeRate, BigDecimal takerFeeRate) {
        this.makerFeeRate = requireNonNegative(makerFeeRate, "makerFeeRate");
        this.takerFeeRate = requireNonNegative(takerFeeRate, "takerFeeRate");
    }

    @Override
    public BigDecimal calculate(BigDecimal notional, LiquidityRole liquidityRole) {
        if (notional == null || notional.signum() < 0) {
            throw new IllegalArgumentException("notional must not be negative");
        }
        BigDecimal rate = Objects.requireNonNull(liquidityRole, "liquidityRole") == LiquidityRole.MAKER
                ? makerFeeRate : takerFeeRate;
        return notional.multiply(rate);
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }
}
