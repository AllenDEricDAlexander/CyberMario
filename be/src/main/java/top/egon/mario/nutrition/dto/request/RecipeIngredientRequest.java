package top.egon.mario.nutrition.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request body for a family recipe ingredient.
 */
public record RecipeIngredientRequest(
        @NotBlank @Size(max = 128) String foodName,
        @Size(max = 64) String category,
        @NotNull @DecimalMin("0.0") BigDecimal amount,
        @NotBlank @Size(max = 32) String unit,
        Boolean optional
) {
}
