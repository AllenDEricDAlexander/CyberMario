package top.egon.mario.nutrition.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Nutrition member health profile response DTO.
 */
public record HealthProfileResponse(
        Long id,
        Long familyId,
        Long memberProfileId,
        String activityLevel,
        List<String> dietGoals,
        List<String> allergyTags,
        List<String> dislikeTags,
        List<String> restrictionTags,
        BigDecimal targetCalories,
        BigDecimal targetProtein,
        BigDecimal targetFat,
        BigDecimal targetCarbs,
        BigDecimal targetSodium,
        BigDecimal targetSugar,
        Instant createdAt,
        Instant updatedAt
) {
}
