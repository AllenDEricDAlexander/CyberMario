package top.egon.mario.nutrition.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request body for a family recipe ingredient.
 */
public record RecipeIngredientRequest(
        @Min(1) Long standardFoodId,
        @NotBlank @Size(max = 128) String foodName,
        @Size(max = 64) String category,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
        @NotBlank @Size(max = 32) String unit,
        @DecimalMin(value = "0.0", inclusive = false) BigDecimal gramsPerUnit,
        Boolean optional
) {

    public RecipeIngredientRequest(String foodName, String category, BigDecimal amount,
                                   String unit, Boolean optional) {
        this(null, foodName, category, amount, unit, null, optional);
    }
}
