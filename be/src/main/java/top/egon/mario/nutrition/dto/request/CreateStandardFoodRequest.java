package top.egon.mario.nutrition.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request body for creating a platform standard food.
 */
public record CreateStandardFoodRequest(
        @NotBlank @Size(max = 128) String nameCn,
        @Size(max = 128) String nameEn,
        @NotBlank @Size(max = 64) String category,
        @DecimalMin("0.0") BigDecimal caloriesPer100g,
        @DecimalMin("0.0") BigDecimal proteinPer100g,
        @DecimalMin("0.0") BigDecimal fatPer100g,
        @DecimalMin("0.0") BigDecimal carbsPer100g
) {
}
