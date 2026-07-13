package top.egon.mario.nutrition.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import top.egon.mario.nutrition.po.enums.NutritionMealType;

import java.math.BigDecimal;

/**
 * Editable meal-plan dish request.
 */
public record MealPlanItemRequest(
        Long id,
        @NotNull NutritionMealType mealType,
        @NotNull @Min(1) Long recipeId,
        @NotNull @DecimalMin("0.001") BigDecimal servingCount,
        @NotNull @Min(0) Integer sortOrder
) {
}
