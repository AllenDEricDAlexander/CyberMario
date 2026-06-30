package top.egon.mario.nutrition.dto.response;

import top.egon.mario.nutrition.po.enums.NutritionMealPlanStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Meal plan response DTO for review and confirmation workflows.
 */
public record MealPlanResponse(
        Long id,
        Long familyId,
        Long aiRecommendationId,
        LocalDate planDate,
        NutritionMealPlanStatus status,
        String title,
        Instant publishedAt,
        Instant confirmationCutoffAt,
        int confirmedMemberCount,
        BigDecimal estimatedCost,
        List<MealPlanItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
}
