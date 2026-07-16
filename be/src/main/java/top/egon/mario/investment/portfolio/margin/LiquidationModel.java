package top.egon.mario.investment.portfolio.margin;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Conservative isolated-position liquidation threshold using mark price.
 */
public final class LiquidationModel {

    private final IsolatedMarginModel marginModel;

    public LiquidationModel(IsolatedMarginModel marginModel) {
        this.marginModel = Objects.requireNonNull(marginModel, "marginModel");
    }

    public LiquidationResult evaluate(IsolatedPosition position, BigDecimal markPrice,
                                      BigDecimal contractMultiplier, PositionTier tier,
                                      BigDecimal closeFeeRate) {
        Objects.requireNonNull(position, "position");
        if (markPrice == null || markPrice.signum() <= 0) {
            throw new IllegalArgumentException("markPrice must be positive");
        }
        if (tier == null) {
            throw new IllegalArgumentException("position tier is required");
        }
        if (closeFeeRate == null || closeFeeRate.signum() < 0) {
            throw new IllegalArgumentException("closeFeeRate must not be negative");
        }
        BigDecimal notional = marginModel.notional(position.quantity(), markPrice, contractMultiplier);
        if (!tier.contains(notional)) {
            throw new IllegalArgumentException("position tier does not cover current notional");
        }
        BigDecimal unrealizedPnl = marginModel.unrealizedPnl(position.positionSide(), position.entryPrice(),
                markPrice, position.quantity(), contractMultiplier);
        BigDecimal positionEquity = position.positionMargin().add(unrealizedPnl);
        BigDecimal maintenanceMargin = marginModel.maintenanceMargin(
                notional, tier.maintenanceMarginRate());
        BigDecimal estimatedCloseFee = notional.multiply(closeFeeRate);
        boolean required = positionEquity.compareTo(maintenanceMargin.add(estimatedCloseFee)) <= 0;
        return new LiquidationResult(required, notional, unrealizedPnl, positionEquity,
                maintenanceMargin, estimatedCloseFee);
    }
}
