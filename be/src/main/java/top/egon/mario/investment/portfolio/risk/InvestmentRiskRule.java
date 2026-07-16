package top.egon.mario.investment.portfolio.risk;

import java.time.Instant;
import java.util.List;

/**
 * Strategy contract for a cohesive, ordered group of deterministic risk checks.
 */
public interface InvestmentRiskRule {

    int order();

    List<InvestmentRiskCheckResult> evaluate(
            InvestmentRiskContext context, InvestmentRiskLimits limits, Instant checkedAt);
}
