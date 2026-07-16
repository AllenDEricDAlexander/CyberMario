package top.egon.mario.investment.portfolio.web.dto;

import java.time.Instant;

public record InvestmentEquityResponse(
        Instant snapshotTime,
        String walletBalance,
        String usedMargin,
        String maintenanceMargin,
        String unrealizedPnl,
        String equity,
        String availableBalance,
        String grossExposure,
        String totalReturn,
        String drawdown,
        Long positionCount
) {
}
