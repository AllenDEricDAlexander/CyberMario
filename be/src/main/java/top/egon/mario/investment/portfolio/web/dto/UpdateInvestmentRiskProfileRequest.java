package top.egon.mario.investment.portfolio.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Full replacement of account limits guarded by optimistic version.
 */
public record UpdateInvestmentRiskProfileRequest(
        @NotNull @Min(0) Long version,
        @NotBlank String maxLeverage,
        @NotBlank String maxOrderNotional,
        @NotBlank String maxPositionNotional,
        @NotBlank String maxGrossExposureNotional,
        @NotNull @Min(1) Long maxOpenPositions,
        @NotBlank String maxDailyLossAmount,
        @NotBlank String maxDrawdownRatio,
        @NotNull @Min(1) Long maxOrdersPerHour,
        @NotNull @Min(0) Long cooldownSeconds,
        @NotNull @Min(1) Long maxMarketDataAgeSeconds,
        @NotBlank String maxSlippageBps
) {
    public InvestmentRiskProfileRequest profile() {
        return new InvestmentRiskProfileRequest(
                maxLeverage, maxOrderNotional, maxPositionNotional, maxGrossExposureNotional,
                maxOpenPositions, maxDailyLossAmount, maxDrawdownRatio, maxOrdersPerHour,
                cooldownSeconds, maxMarketDataAgeSeconds, maxSlippageBps);
    }
}
