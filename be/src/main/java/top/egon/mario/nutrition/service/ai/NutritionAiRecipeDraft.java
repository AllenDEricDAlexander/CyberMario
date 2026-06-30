package top.egon.mario.nutrition.service.ai;

import top.egon.mario.nutrition.po.enums.NutritionMealType;

import java.math.BigDecimal;

/**
 * Normalized AI recipe draft used to create review-only meal plan items.
 */
public record NutritionAiRecipeDraft(
        NutritionMealType mealType,
        String name,
        BigDecimal servingCount,
        String reason
) {
}
