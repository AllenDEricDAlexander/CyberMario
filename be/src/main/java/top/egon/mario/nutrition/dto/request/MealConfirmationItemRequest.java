package top.egon.mario.nutrition.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Dish-level participation and serving selection for one member confirmation.
 */
public record MealConfirmationItemRequest(
        @NotNull @Min(1) Long mealPlanItemId,
        @NotNull Boolean selected,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal servingCount,
        @NotNull Boolean riskAcknowledged,
        @Size(max = 512) String adjustmentNote
) {
}
