package top.egon.mario.investment.trading.matching.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Contract precision facts frozen for one simulation.
 */
public record ContractTerms(
        BigDecimal priceStep,
        BigDecimal quantityStep,
        BigDecimal contractMultiplier
) {

    public ContractTerms {
        requirePositive(priceStep, "priceStep");
        requirePositive(quantityStep, "quantityStep");
        requirePositive(contractMultiplier, "contractMultiplier");
    }

    public BigDecimal roundQuantity(BigDecimal quantity) {
        requirePositive(quantity, "quantity");
        return roundToStep(quantity, quantityStep, RoundingMode.FLOOR);
    }

    public BigDecimal roundLimitPrice(BigDecimal price, TradeSide tradeSide) {
        requirePositive(price, "price");
        if (tradeSide == null) {
            throw new IllegalArgumentException("tradeSide is required");
        }
        return roundToStep(price, priceStep,
                tradeSide == TradeSide.BUY ? RoundingMode.FLOOR : RoundingMode.CEILING);
    }

    public BigDecimal roundExecutionPrice(BigDecimal price, TradeSide tradeSide) {
        requirePositive(price, "price");
        if (tradeSide == null) {
            throw new IllegalArgumentException("tradeSide is required");
        }
        return roundToStep(price, priceStep,
                tradeSide == TradeSide.BUY ? RoundingMode.CEILING : RoundingMode.FLOOR);
    }

    private static BigDecimal roundToStep(BigDecimal value, BigDecimal step, RoundingMode roundingMode) {
        return value.divide(step, 0, roundingMode).multiply(step);
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
