package top.egon.mario.investment.portfolio.margin;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Resolves a required tier without defaulting missing market data.
 */
public final class PositionTierResolver {

    public PositionTier resolve(BigDecimal notional, List<PositionTier> tiers) {
        if (notional == null || notional.signum() < 0) {
            throw new IllegalArgumentException("notional must not be negative");
        }
        if (tiers == null || tiers.isEmpty()) {
            throw new IllegalArgumentException("position tier data is required");
        }
        return tiers.stream()
                .sorted(Comparator.comparing(PositionTier::minimumNotional)
                        .thenComparingInt(PositionTier::tierLevel))
                .filter(tier -> tier.contains(notional))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No position tier covers notional " + notional));
    }
}
