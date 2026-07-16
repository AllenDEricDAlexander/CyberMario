package top.egon.mario.investment.portfolio.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Complete risk input. Decimal values use the module's lossless string contract.
 */
public record InvestmentRiskProfileRequest(
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
}
