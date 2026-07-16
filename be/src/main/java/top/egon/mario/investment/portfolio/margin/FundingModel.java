package top.egon.mario.investment.portfolio.margin;

import top.egon.mario.investment.common.model.PositionSide;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Funding cash-flow formula that rejects missing settlement data.
 */
public final class FundingModel {

    private final IsolatedMarginModel marginModel;

    public FundingModel(IsolatedMarginModel marginModel) {
        this.marginModel = Objects.requireNonNull(marginModel, "marginModel");
    }

    public FundingResult calculate(PositionSide positionSide, BigDecimal quantity, BigDecimal markPrice,
                                   BigDecimal contractMultiplier, BigDecimal fundingRate) {
        Objects.requireNonNull(positionSide, "positionSide");
        if (markPrice == null || markPrice.signum() <= 0) {
            throw new IllegalArgumentException("markPrice must be positive");
        }
        if (fundingRate == null) {
            throw new IllegalArgumentException("fundingRate is required");
        }
        BigDecimal notional = marginModel.notional(quantity, markPrice, contractMultiplier);
        BigDecimal transfer = notional.multiply(fundingRate);
        BigDecimal cashFlow = positionSide == PositionSide.LONG ? transfer.negate() : transfer;
        return new FundingResult(notional, fundingRate, cashFlow);
    }
}
