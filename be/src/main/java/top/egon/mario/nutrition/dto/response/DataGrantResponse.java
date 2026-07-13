package top.egon.mario.nutrition.dto.response;

import top.egon.mario.nutrition.po.enums.NutritionGrantDataScope;
import top.egon.mario.nutrition.po.enums.NutritionGrantPermissionLevel;
import top.egon.mario.nutrition.po.enums.NutritionStatus;

import java.time.Instant;

/**
 * Nutrition data grant response DTO.
 */
public record DataGrantResponse(
        Long id,
        Long familyId,
        Long memberProfileId,
        String granteeType,
        Long granteeId,
        NutritionGrantDataScope dataScope,
        NutritionGrantPermissionLevel permissionLevel,
        Instant expiresAt,
        NutritionStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
