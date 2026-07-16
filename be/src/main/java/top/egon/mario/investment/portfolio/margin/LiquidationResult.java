package top.egon.mario.investment.portfolio.margin;

import java.math.BigDecimal;

public record LiquidationResult(
        boolean liquidationRequired,
        BigDecimal notional,
        BigDecimal unrealizedPnl,
        BigDecimal positionEquity,
        BigDecimal maintenanceMargin,
        BigDecimal estimatedCloseFee
) {
}
