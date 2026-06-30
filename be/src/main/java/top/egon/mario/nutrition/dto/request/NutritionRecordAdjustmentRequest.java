package top.egon.mario.nutrition.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for appending a nutrition record correction.
 */
public record NutritionRecordAdjustmentRequest(
        @Valid @NotNull NutritionNutrientsRequest nutrients,
        @Size(max = 512) String reason
) {
}
