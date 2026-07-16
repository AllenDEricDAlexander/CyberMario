package top.egon.mario.investment.portfolio.margin;

import java.math.BigDecimal;

/**
 * Signed account cash flow for one funding settlement.
 */
public record FundingResult(
        BigDecimal notional,
        BigDecimal fundingRate,
        BigDecimal cashFlow
) {
}
