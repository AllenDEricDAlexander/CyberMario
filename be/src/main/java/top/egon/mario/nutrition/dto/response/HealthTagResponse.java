package top.egon.mario.nutrition.dto.response;

import top.egon.mario.nutrition.po.enums.NutritionStatus;

import java.time.Instant;

/**
 * Nutrition health tag response DTO.
 */
public record HealthTagResponse(
        Long id,
        String tagType,
        String tagCode,
        String name,
        String description,
        int sortOrder,
        NutritionStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
