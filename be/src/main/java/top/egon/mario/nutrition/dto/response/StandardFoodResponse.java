package top.egon.mario.nutrition.dto.response;

import top.egon.mario.nutrition.po.enums.NutritionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Platform standard food response DTO.
 */
public record StandardFoodResponse(
        Long id,
        String nameCn,
        String nameEn,
        List<String> aliases,
        String category,
        String externalSource,
        String externalFoodId,
        BigDecimal caloriesPer100g,
        BigDecimal proteinPer100g,
        BigDecimal fatPer100g,
        BigDecimal carbsPer100g,
        BigDecimal sugarPer100g,
        BigDecimal sodiumPer100g,
        BigDecimal fiberPer100g,
        BigDecimal cholesterolPer100g,
        String purineLevel,
        BigDecimal giValue,
        List<String> allergenTags,
        List<String> suitableTags,
        String dataQuality,
        NutritionStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
