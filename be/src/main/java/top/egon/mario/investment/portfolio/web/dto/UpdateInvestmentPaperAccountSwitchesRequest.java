package top.egon.mario.investment.portfolio.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Explicit account switch replacement guarded by optimistic version.
 */
public record UpdateInvestmentPaperAccountSwitchesRequest(
        @NotNull Boolean tradingEnabled,
        @NotNull Boolean agentAutoTradeEnabled,
        @NotNull @Min(0) Long version
) {
}
