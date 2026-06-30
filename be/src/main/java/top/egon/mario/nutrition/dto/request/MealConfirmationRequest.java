package top.egon.mario.nutrition.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import top.egon.mario.nutrition.po.enums.NutritionMealType;

import java.util.List;

/**
 * Request body for confirming one member's meal plan participation.
 */
public record MealConfirmationRequest(
        @NotNull @Min(1) Long memberProfileId,
        Boolean eatAtHome,
        List<NutritionMealType> selectedMealTypes,
        Boolean riskConfirmed,
        @Size(max = 512) String riskConfirmationNote,
        @Size(max = 512) String remark
) {
}
