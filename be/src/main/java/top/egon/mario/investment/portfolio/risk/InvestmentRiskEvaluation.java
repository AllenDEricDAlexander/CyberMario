package top.egon.mario.investment.portfolio.risk;

import java.util.List;

/**
 * Complete ordered risk decision. Any failed result rejects the intent.
 */
public record InvestmentRiskEvaluation(boolean passed, List<InvestmentRiskCheckResult> results) {
    public InvestmentRiskEvaluation {
        results = List.copyOf(results);
    }
}
