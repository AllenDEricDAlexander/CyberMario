package top.egon.mario.nutrition.dto.response;

import top.egon.mario.nutrition.po.enums.NutritionStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Platform standard food response DTO.
 */
public record StandardFoodResponse(
        Long id,
        String nameCn,
        String nameEn,
        String category,
        BigDecimal caloriesPer100g,
        BigDecimal proteinPer100g,
        BigDecimal fatPer100g,
        BigDecimal carbsPer100g,
        NutritionStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
