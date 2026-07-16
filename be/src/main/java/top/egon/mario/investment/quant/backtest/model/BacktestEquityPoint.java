package top.egon.mario.investment.quant.backtest.model;

import java.math.BigDecimal;
import java.time.Instant;

public record BacktestEquityPoint(Instant pointTime, BigDecimal walletBalance,
                                  BigDecimal usedMargin, BigDecimal unrealizedPnl,
                                  BigDecimal equity, BigDecimal drawdown,
                                  BigDecimal grossExposure, boolean eventPoint) {
}
