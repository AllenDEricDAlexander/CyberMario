package top.egon.mario.nutrition.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import top.egon.mario.nutrition.po.enums.NutritionMealType;

import java.time.LocalTime;
import java.util.List;

/**
 * Request body for replacing editable nutrition family settings.
 */
public record UpdateFamilySettingsRequest(
        @Size(max = 64) String region,
        @Size(min = 3, max = 3) String currency,
        List<@NotNull NutritionMealType> defaultMealTypes,
        @NotNull Boolean aiEnabled,
        LocalTime aiGenerateTime,
        @NotNull Boolean healthAlertEnabled,
        @NotNull Boolean budgetEnabled
) {
}
