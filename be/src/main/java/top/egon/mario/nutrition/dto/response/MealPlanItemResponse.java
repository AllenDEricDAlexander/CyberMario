package top.egon.mario.nutrition.dto.response;

import top.egon.mario.nutrition.po.enums.NutritionMealType;

import java.math.BigDecimal;

/**
 * Meal plan dish response DTO.
 */
public record MealPlanItemResponse(
        Long id,
        Long mealPlanId,
        NutritionMealType mealType,
        Long recipeId,
        String dishName,
        BigDecimal servingCount,
        int sortOrder
) {
}
