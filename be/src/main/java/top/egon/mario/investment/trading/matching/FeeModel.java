package top.egon.mario.investment.trading.matching;

import top.egon.mario.investment.trading.matching.model.LiquidityRole;

import java.math.BigDecimal;

/**
 * Computes a non-negative execution fee from immutable fill facts.
 */
public interface FeeModel {

    BigDecimal calculate(BigDecimal notional, LiquidityRole liquidityRole);
}
