package top.egon.mario.investment.quant.backtest.model;

import top.egon.mario.investment.common.model.PositionSide;

import java.math.BigDecimal;
import java.time.Instant;

public record BacktestTrade(long instrumentId, PositionSide positionSide,
                            Instant entryTime, Instant exitTime,
                            BigDecimal entryPrice, BigDecimal exitPrice,
                            BigDecimal quantity, BigDecimal leverage,
                            BigDecimal grossPnl, BigDecimal feeAmount,
                            BigDecimal fundingAmount, BigDecimal netPnl,
                            String exitReason) {
}
