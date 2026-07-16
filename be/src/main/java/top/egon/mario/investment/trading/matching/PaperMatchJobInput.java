package top.egon.mario.investment.trading.matching;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable matching terms persisted in the durable job input.
 */
public record PaperMatchJobInput(
        long orderId,
        long workspaceId,
        long accountId,
        long instrumentId,
        long sourceId,
        Instant eligibleAfter,
        BigDecimal priceStep,
        BigDecimal quantityStep,
        BigDecimal contractMultiplier,
        BigDecimal makerFeeRate,
        BigDecimal takerFeeRate,
        BigDecimal slippageBps,
        BigDecimal maintenanceMarginRate
) {
}
