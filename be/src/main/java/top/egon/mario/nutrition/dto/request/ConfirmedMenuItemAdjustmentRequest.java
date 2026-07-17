package top.egon.mario.nutrition.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Final serving adjustment for one confirmed meal-plan item.
 */
public record ConfirmedMenuItemAdjustmentRequest(
        @NotNull @Min(1) Long mealPlanItemId,
        @NotNull @DecimalMin("0.000") BigDecimal finalServingCount
) {
}
