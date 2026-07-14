package top.egon.mario.nutrition.dto.response;

import top.egon.mario.nutrition.service.calculation.NutritionTotals;

import java.math.BigDecimal;

/**
 * Family recipe ingredient response DTO.
 */
public record RecipeIngredientResponse(
        Long id,
        Long recipeId,
        Long standardFoodId,
        String rawFoodName,
        BigDecimal amount,
        String unit,
        BigDecimal gramsPerUnit,
        String mappingStatus,
        boolean optional,
        NutritionTotals nutritionSnapshot
) {
}
