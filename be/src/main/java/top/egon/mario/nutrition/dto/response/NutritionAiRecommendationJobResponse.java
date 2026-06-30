package top.egon.mario.nutrition.dto.response;

import top.egon.mario.nutrition.po.enums.NutritionAiJobStatus;
import top.egon.mario.nutrition.po.enums.NutritionAiTriggerType;
import top.egon.mario.nutrition.po.enums.NutritionMealType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * AI recommendation job response DTO.
 */
public record NutritionAiRecommendationJobResponse(
        Long id,
        Long familyId,
        NutritionAiTriggerType triggerType,
        NutritionAiJobStatus status,
        Long requestedBy,
        LocalDate plannedDate,
        List<NutritionMealType> targetMealTypes,
        Long recommendationId,
        Long mealPlanId,
        String errorMessage,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
