package top.egon.mario.nutrition.service.rule;

import org.springframework.stereotype.Component;
import top.egon.mario.nutrition.po.enums.NutritionRiskLevel;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class BudgetRuleChecker implements NutritionRuleChecker {

    public static final String RULE_CODE = "BUDGET";

    @Override
    public int order() {
        return 40;
    }

    @Override
    public List<RuleCheckResult> check(RuleCheckRequest request) {
        if (request.budgetContext() == null || request.budgetContext().perMealLimit() == null
                || request.estimatedCost() == null) {
            return List.of();
        }
        BigDecimal perMealLimit = request.budgetContext().perMealLimit();
        if (request.estimatedCost().compareTo(perMealLimit) <= 0) {
            return List.of();
        }
        return List.of(new RuleCheckResult(null, RULE_CODE, NutritionRiskLevel.MEDIUM,
                "Estimated cost %s exceeds per-meal budget %s".formatted(request.estimatedCost(), perMealLimit),
                false, false,
                Map.of("estimatedCost", request.estimatedCost(), "perMealLimit", perMealLimit)));
    }
}
