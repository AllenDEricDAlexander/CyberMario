package top.egon.mario.nutrition.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import top.egon.mario.nutrition.po.enums.NutritionStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request body for creating a platform standard food.
 */
public record CreateStandardFoodRequest(
        @NotBlank @Size(max = 128) String nameCn,
        @Size(max = 128) String nameEn,
        List<@NotBlank @Size(max = 128) String> aliases,
        @NotBlank @Size(max = 64) String category,
        @Size(max = 64) String externalSource,
        @Size(max = 128) String externalFoodId,
        @DecimalMin("0.0") BigDecimal caloriesPer100g,
        @DecimalMin("0.0") BigDecimal proteinPer100g,
        @DecimalMin("0.0") BigDecimal fatPer100g,
        @DecimalMin("0.0") BigDecimal carbsPer100g,
        @DecimalMin("0.0") BigDecimal sugarPer100g,
        @DecimalMin("0.0") BigDecimal sodiumPer100g,
        @DecimalMin("0.0") BigDecimal fiberPer100g,
        @DecimalMin("0.0") BigDecimal cholesterolPer100g,
        @Size(max = 32) String purineLevel,
        @DecimalMin("0.0") BigDecimal giValue,
        List<@NotBlank @Size(max = 128) String> allergenTags,
        List<@NotBlank @Size(max = 128) String> suitableTags,
        @NotBlank @Size(max = 32) String dataQuality,
        @NotNull NutritionStatus status
) {
}
