package top.egon.mario.investment.trading.matching;

import top.egon.mario.investment.trading.matching.model.TradeSide;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

/**
 * Conservative fixed-basis-point slippage for market fills.
 */
public final class FixedBpsSlippageModel implements SlippageModel {

    private static final BigDecimal BPS_DIVISOR = new BigDecimal("10000");

    private final BigDecimal slippageBps;

    public FixedBpsSlippageModel(BigDecimal slippageBps) {
        this.slippageBps = requireNonNegative(slippageBps, "slippageBps");
    }

    @Override
    public BigDecimal apply(BigDecimal referencePrice, TradeSide tradeSide) {
        BigDecimal price = requirePositive(referencePrice, "referencePrice");
        Objects.requireNonNull(tradeSide, "tradeSide");
        BigDecimal ratio = slippageBps.divide(BPS_DIVISOR, MathContext.DECIMAL128);
        BigDecimal factor = tradeSide == TradeSide.BUY
                ? BigDecimal.ONE.add(ratio)
                : BigDecimal.ONE.subtract(ratio);
        if (factor.signum() <= 0) {
            throw new IllegalArgumentException("slippageBps produces a non-positive execution price");
        }
        return price.multiply(factor);
    }

    private static BigDecimal requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }
}
