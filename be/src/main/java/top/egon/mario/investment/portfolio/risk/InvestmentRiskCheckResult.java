package top.egon.mario.investment.portfolio.risk;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * One explainable, independently persistable deterministic rule result.
 */
public record InvestmentRiskCheckResult(
        InvestmentRiskRuleCode ruleCode,
        boolean passed,
        BigDecimal observedValue,
        BigDecimal limitValue,
        String message,
        Map<String, String> details,
        Instant checkedAt
) {
    public InvestmentRiskCheckResult {
        Objects.requireNonNull(ruleCode, "ruleCode");
        Objects.requireNonNull(message, "message");
        details = details == null ? Map.of() : Map.copyOf(details);
        Objects.requireNonNull(checkedAt, "checkedAt");
    }
}
