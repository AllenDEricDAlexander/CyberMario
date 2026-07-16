package top.egon.mario.investment.trading.service.model;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountSnapshotResult(
        long accountId,
        Instant snapshotTime,
        BigDecimal walletBalance,
        BigDecimal usedMargin,
        BigDecimal maintenanceMargin,
        BigDecimal unrealizedPnl,
        BigDecimal equity,
        BigDecimal availableBalance,
        BigDecimal grossExposure,
        BigDecimal totalReturn,
        BigDecimal drawdown,
        long positionCount
) {
}
