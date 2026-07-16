package top.egon.mario.investment.trading.service.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Mark, tier, fee, and contract facts used by one margin or liquidation decision. */
public record PaperMaintenanceMarketSnapshot(
        BigDecimal markPrice,
        Instant marketTime,
        long marketRevision,
        BigDecimal priceStep,
        BigDecimal quantityStep,
        BigDecimal contractMultiplier,
        BigDecimal takerFeeRate,
        BigDecimal slippageBps,
        BigDecimal maintenanceMarginRate,
        Instant tierObservedAt
) {
}
