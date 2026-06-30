package top.egon.mario.nutrition.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request body fragment for supplied nutrition values.
 */
public record NutritionNutrientsRequest(
        @NotNull @DecimalMin("0.000") BigDecimal calories,
        @NotNull @DecimalMin("0.000") BigDecimal protein,
        @NotNull @DecimalMin("0.000") BigDecimal fat,
        @NotNull @DecimalMin("0.000") BigDecimal carbs,
        @NotNull @DecimalMin("0.000") BigDecimal sugar,
        @NotNull @DecimalMin("0.000") BigDecimal sodium,
        @NotNull @DecimalMin("0.000") BigDecimal fiber,
        @NotNull @DecimalMin("0.000") BigDecimal cholesterol
) {
}
