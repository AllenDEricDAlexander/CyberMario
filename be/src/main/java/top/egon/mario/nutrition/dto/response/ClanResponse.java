package top.egon.mario.nutrition.dto.response;

import top.egon.mario.nutrition.po.enums.NutritionStatus;

import java.time.Instant;

/**
 * Nutrition clan response DTO.
 */
public record ClanResponse(
        Long id,
        String name,
        Long ownerUserId,
        NutritionStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
