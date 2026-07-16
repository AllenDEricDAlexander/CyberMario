package top.egon.mario.investment.trading.service.model;

import top.egon.mario.investment.portfolio.risk.InvestmentRiskCheckResult;

import java.util.List;

/**
 * Idempotent execution summary returned by every facade caller.
 */
public record PaperTradeResult(
        Long intentId,
        String intentStatus,
        List<InvestmentRiskCheckResult> riskResults,
        PaperOrderSummary order,
        PaperFillSummary fill
) {
    public PaperTradeResult {
        riskResults = List.copyOf(riskResults);
    }
}
