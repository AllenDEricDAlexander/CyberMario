package top.egon.mario.nutrition.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import top.egon.mario.nutrition.po.enums.NutritionMealType;

import java.time.LocalDate;
import java.util.List;

/**
 * Request body for manually generating an AI nutrition recommendation.
 */
public record GenerateAiRecommendationRequest(
        @NotNull LocalDate plannedDate,
        @Size(max = 4) List<@NotNull NutritionMealType> mealTypes
) {
}
