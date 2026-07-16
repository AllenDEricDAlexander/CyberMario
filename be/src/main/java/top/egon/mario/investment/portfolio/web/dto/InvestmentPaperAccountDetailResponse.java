package top.egon.mario.investment.portfolio.web.dto;

/**
 * Account creation/detail response including its mandatory one-to-one risk profile.
 */
public record InvestmentPaperAccountDetailResponse(
        InvestmentPaperAccountResponse account,
        InvestmentRiskProfileResponse riskProfile
) {
}
