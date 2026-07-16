package top.egon.mario.investment.trading.service.model;

import top.egon.mario.investment.trading.matching.model.ContractTerms;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable market/spec/tier facts read before the account-locking transaction.
 */
public record PaperAcceptanceMarketSnapshot(
        long sourceId,
        long instrumentId,
        boolean subscribed,
        boolean tradable,
        BigDecimal markPrice,
        Instant quoteSourceTime,
        long quoteVersion,
        long contractRevision,
        BigDecimal priceStep,
        BigDecimal quantityStep,
        BigDecimal contractMultiplier,
        BigDecimal makerFeeRate,
        BigDecimal takerFeeRate,
        BigDecimal contractMaxLeverage,
        Instant tierObservedAt,
        BigDecimal tierMaxLeverage,
        BigDecimal maintenanceMarginRate,
        boolean fundingAvailable,
        BigDecimal slippageBps
) {
    public boolean completeContractTerms() {
        return positive(priceStep) && positive(quantityStep) && positive(contractMultiplier)
                && nonNegative(makerFeeRate) && nonNegative(takerFeeRate)
                && positive(contractMaxLeverage) && contractRevision > 0;
    }

    public boolean tierAvailable() {
        return tierObservedAt != null && positive(tierMaxLeverage)
                && nonNegative(maintenanceMarginRate) && maintenanceMarginRate.compareTo(BigDecimal.ONE) < 0;
    }

    public ContractTerms contractTerms() {
        if (!completeContractTerms()) {
            throw new IllegalStateException("Complete contract terms are unavailable");
        }
        return new ContractTerms(priceStep, quantityStep, contractMultiplier);
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static boolean nonNegative(BigDecimal value) {
        return value != null && value.signum() >= 0;
    }
}
