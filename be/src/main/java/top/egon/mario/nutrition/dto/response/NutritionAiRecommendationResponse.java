package top.egon.mario.nutrition.dto.response;

import top.egon.mario.nutrition.po.enums.NutritionMealType;
import top.egon.mario.nutrition.po.enums.NutritionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * AI nutrition recommendation response DTO.
 */
public record NutritionAiRecommendationResponse(
        Long id,
        Long familyId,
        Long aiJobId,
        LocalDate recommendationDate,
        String title,
        String reason,
        List<NutritionMealType> mealTypes,
        BigDecimal costEstimate,
        NutritionStatus status,
        Long mealPlanId,
        Instant createdAt,
        Instant updatedAt
) {
}
