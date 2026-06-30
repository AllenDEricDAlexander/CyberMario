package top.egon.mario.nutrition.dto.response;

import top.egon.mario.nutrition.po.enums.NutritionMealType;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Nutrition record returned by generated, adjustment, and extra-food operations.
 */
public record NutritionRecordResponse(
        Long id,
        Long familyId,
        Long memberProfileId,
        Long mealPlanId,
        Long mealConfirmationId,
        LocalDate recordDate,
        NutritionMealType mealType,
        String sourceType,
        NutritionNutrientsResponse nutrients,
        String riskTags,
        String calculationSnapshot,
        String metadataJson,
        Instant createdAt,
        Instant updatedAt
) {
}
