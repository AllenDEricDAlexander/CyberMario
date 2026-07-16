package top.egon.mario.investment.quant.web.dto;

import java.time.Instant;

public record InvestmentBacktestTradeResponse(Long tradeId, Long instrumentId, String positionSide,
                                              Instant entryTime, Instant exitTime,
                                              String entryPrice, String exitPrice, String quantity,
                                              String leverage, String grossPnl, String feeAmount,
                                              String fundingAmount, String netPnl, String exitReason) {
}
