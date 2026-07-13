package top.egon.mario.nutrition.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request body for mapping a recipe ingredient to a standard food.
 */
public record UpdateRecipeIngredientMappingRequest(
        @NotNull @Min(1) Long standardFoodId,
        @DecimalMin(value = "0.0", inclusive = false) BigDecimal gramsPerUnit
) {
}
