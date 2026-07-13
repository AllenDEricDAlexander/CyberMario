package top.egon.mario.nutrition.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import top.egon.mario.nutrition.po.enums.NutritionMealType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for recording food outside the generated meal plan.
 */
public record CreateExtraFoodRecordRequest(
        @NotNull @Min(1) Long memberProfileId,
        @NotNull LocalDate recordDate,
        @NotNull NutritionMealType mealType,
        @NotBlank @Size(max = 128) String foodName,
        @Min(1) Long standardFoodId,
        @NotNull @DecimalMin(value = "0.000", inclusive = false) BigDecimal amount,
        @NotBlank @Size(max = 32) String unit,
        @Valid NutritionNutrientsRequest nutrients,
        @Size(max = 512) String note
) {
}
