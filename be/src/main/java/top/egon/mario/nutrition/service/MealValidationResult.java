package top.egon.mario.nutrition.service;

import top.egon.mario.nutrition.service.calculation.NutritionTotals;
import top.egon.mario.nutrition.service.rule.RuleCheckResult;

import java.math.BigDecimal;
import java.util.List;

/**
 * Deterministic validation outcome for a persisted meal plan.
 */
public record MealValidationResult(
        boolean publishable,
        NutritionTotals nutritionTotals,
        BigDecimal estimatedCost,
        List<RuleCheckResult> risks,
        List<String> errors
) {
}
