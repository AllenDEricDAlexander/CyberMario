package top.egon.mario.nutrition.service.ai;

import top.egon.mario.nutrition.po.enums.NutritionMealType;
import top.egon.mario.nutrition.service.calculation.NutritionTotals;

import java.math.BigDecimal;

/**
 * Validated real recipe referenced by a generated meal plan item.
 */
public record MaterializedNutritionRecipe(
        Long recipeId,
        NutritionMealType mealType,
        String dishName,
        BigDecimal servingCount,
        NutritionTotals nutrients,
        BigDecimal estimatedCost
) {
}
