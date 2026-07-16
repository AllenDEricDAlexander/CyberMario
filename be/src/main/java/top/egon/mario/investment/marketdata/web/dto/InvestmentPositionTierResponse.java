package top.egon.mario.investment.marketdata.web.dto;

import java.time.Instant;

/**
 * One tier from the latest complete provider snapshot no later than the cutoff.
 */
public record InvestmentPositionTierResponse(
        int tierLevel,
        String startNotional,
        String endNotional,
        String maxLeverage,
        String maintenanceMarginRate,
        Instant observedAt,
        Instant dataAsOf
) {
}
