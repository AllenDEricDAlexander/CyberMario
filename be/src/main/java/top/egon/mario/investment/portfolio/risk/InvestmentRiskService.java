package top.egon.mario.investment.portfolio.risk;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;

/**
 * Pure deterministic risk orchestrator. Persistence belongs to the trading transaction phase.
 */
@Service
public class InvestmentRiskService {

    private final List<InvestmentRiskRule> rules;
    private final Clock clock;

    public InvestmentRiskService(List<InvestmentRiskRule> rules, Clock clock) {
        this.rules = rules.stream()
                .sorted(Comparator.comparingInt(InvestmentRiskRule::order))
                .toList();
        this.clock = clock;
    }

    /**
     * Evaluates every rule at one timestamp and fails closed if a required rule is not installed.
     */
    public InvestmentRiskEvaluation evaluate(InvestmentRiskContext context) {
        Instant checkedAt = clock.instant();
        InvestmentRiskLimits limits = context.effectiveLimits();
        EnumMap<InvestmentRiskRuleCode, InvestmentRiskCheckResult> results =
                new EnumMap<>(InvestmentRiskRuleCode.class);
        for (InvestmentRiskRule rule : rules) {
            for (InvestmentRiskCheckResult result : rule.evaluate(context, limits, checkedAt)) {
                if (results.putIfAbsent(result.ruleCode(), result) != null) {
                    throw new IllegalStateException("Duplicate Investment risk rule: " + result.ruleCode());
                }
            }
        }
        for (InvestmentRiskRuleCode ruleCode : InvestmentRiskRuleCode.values()) {
            results.putIfAbsent(ruleCode, new InvestmentRiskCheckResult(
                    ruleCode, false, null, null, "Required risk rule is not installed",
                    java.util.Map.of("reason", "RULE_NOT_INSTALLED"), checkedAt));
        }
        List<InvestmentRiskCheckResult> ordered = new ArrayList<>(results.values());
        ordered.sort(Comparator.comparingInt(result -> result.ruleCode().ordinal()));
        return new InvestmentRiskEvaluation(
                ordered.stream().allMatch(InvestmentRiskCheckResult::passed), ordered);
    }
}
