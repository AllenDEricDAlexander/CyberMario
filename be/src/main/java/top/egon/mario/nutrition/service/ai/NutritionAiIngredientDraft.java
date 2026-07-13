package top.egon.mario.nutrition.service.ai;

import java.math.BigDecimal;

/**
 * Untrusted ingredient candidate returned by the AI model.
 */
public record NutritionAiIngredientDraft(
        String foodName,
        String category,
        Long standardFoodId,
        BigDecimal amount,
        String unit,
        BigDecimal gramsPerUnit,
        boolean optional
) {
}
