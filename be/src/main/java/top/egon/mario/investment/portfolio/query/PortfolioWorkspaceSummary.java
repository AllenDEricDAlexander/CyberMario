package top.egon.mario.investment.portfolio.query;

import java.math.BigDecimal;
import java.time.Instant;

public record PortfolioWorkspaceSummary(
        long workspaceId,
        Instant dataAsOf,
        long accountCount,
        long positionCount,
        BigDecimal walletBalance,
        BigDecimal equity,
        BigDecimal availableBalance,
        BigDecimal unrealizedPnl,
        BigDecimal grossExposure,
        BigDecimal maxDrawdown,
        long riskWarningCount
) {
}
