package top.egon.mario.nutrition.dto.response;

import top.egon.mario.nutrition.po.enums.NutritionStatus;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

/**
 * Nutrition family response DTO.
 */
public record FamilyResponse(
        Long id,
        String name,
        Long ownerUserId,
        String region,
        String currency,
        List<String> defaultMealTypes,
        boolean aiEnabled,
        LocalTime aiGenerateTime,
        boolean healthAlertEnabled,
        boolean budgetEnabled,
        NutritionStatus status,
        Long ownerMemberProfileId,
        Instant createdAt,
        Instant updatedAt
) {
}
