package top.egon.mario.nutrition.dto.response;

import top.egon.mario.nutrition.po.enums.NutritionConfirmationStatus;

import java.time.Instant;
import java.util.List;

/**
 * Meal confirmation response DTO.
 */
public record MealConfirmationResponse(
        Long id,
        Long familyId,
        Long mealPlanId,
        Long memberProfileId,
        Long confirmedByUserId,
        Long proxyByUserId,
        NutritionConfirmationStatus confirmationStatus,
        boolean eatAtHome,
        List<MealConfirmationItemResponse> items,
        String remark,
        Instant confirmedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
