package top.egon.mario.nutrition.dto.response;

import top.egon.mario.nutrition.po.enums.NutritionStatus;

import java.time.Instant;

/**
 * Clan and family relationship response DTO.
 */
public record ClanFamilyRelationResponse(
        Long id,
        Long clanId,
        Long familyId,
        NutritionStatus relationStatus,
        Instant joinedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
