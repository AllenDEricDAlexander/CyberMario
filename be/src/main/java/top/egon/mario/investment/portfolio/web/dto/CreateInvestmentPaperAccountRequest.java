package top.egon.mario.investment.portfolio.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Paper account creation input. Client switch values are accepted only to be forcibly ignored.
 */
public record CreateInvestmentPaperAccountRequest(
        @NotBlank @Size(max = 128) String name,
        @NotBlank String initialEquity,
        Boolean tradingEnabled,
        Boolean agentAutoTradeEnabled,
        @NotNull @Valid InvestmentRiskProfileRequest riskProfile
) {
}
