package top.egon.mario.investment.portfolio.web.dto;

/**
 * Explicit account limits with units preserved in field names.
 */
public record InvestmentRiskProfileResponse(
        Long id,
        Long accountId,
        String maxLeverage,
        String maxOrderNotional,
        String maxPositionNotional,
        String maxGrossExposureNotional,
        Long maxOpenPositions,
        String maxDailyLossAmount,
        String maxDrawdownRatio,
        Long maxOrdersPerHour,
        Long cooldownSeconds,
        Long maxMarketDataAgeSeconds,
        String maxSlippageBps,
        Long version
) {
}
