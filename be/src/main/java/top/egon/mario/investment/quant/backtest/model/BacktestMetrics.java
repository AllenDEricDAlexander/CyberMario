package top.egon.mario.investment.quant.backtest.model;

import java.math.BigDecimal;

public record BacktestMetrics(BigDecimal totalReturn, BigDecimal annualizedReturn,
                              BigDecimal maxDrawdown, BigDecimal sharpeRatio,
                              BigDecimal sortinoRatio, BigDecimal winRate,
                              BigDecimal profitFactor, BigDecimal turnover,
                              long tradeCount, BigDecimal totalFee,
                              BigDecimal totalFunding, long liquidationCount) {
}
