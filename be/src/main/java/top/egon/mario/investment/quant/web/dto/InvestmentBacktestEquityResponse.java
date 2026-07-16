package top.egon.mario.investment.quant.web.dto;

import java.time.Instant;

public record InvestmentBacktestEquityResponse(Instant pointTime, String walletBalance,
                                               String usedMargin, String unrealizedPnl,
                                               String equity, String drawdown, String grossExposure) {
}
