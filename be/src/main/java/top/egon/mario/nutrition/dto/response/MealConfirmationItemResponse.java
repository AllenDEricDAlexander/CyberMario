package top.egon.mario.nutrition.dto.response;

import top.egon.mario.nutrition.po.enums.NutritionMealType;

import java.math.BigDecimal;

/**
 * Persisted dish-level selection for one meal confirmation.
 */
public record MealConfirmationItemResponse(
        Long id,
        Long confirmationId,
        Long mealPlanItemId,
        NutritionMealType mealType,
        boolean selected,
        BigDecimal servingCount,
        boolean riskAcknowledged,
        String adjustmentNote,
        Long version
) {
}
