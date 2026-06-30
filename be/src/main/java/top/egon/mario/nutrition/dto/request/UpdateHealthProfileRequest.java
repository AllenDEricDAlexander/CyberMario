package top.egon.mario.nutrition.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request body for saving a member health profile.
 */
public record UpdateHealthProfileRequest(
        @Size(max = 32) String activityLevel,
        List<@Size(max = 128) String> dietGoals,
        List<@Size(max = 128) String> allergyTags,
        List<@Size(max = 128) String> dislikeTags,
        List<@Size(max = 128) String> restrictionTags,
        @DecimalMin("0.0") BigDecimal targetCalories,
        @DecimalMin("0.0") BigDecimal targetProtein,
        @DecimalMin("0.0") BigDecimal targetFat,
        @DecimalMin("0.0") BigDecimal targetCarbs,
        @DecimalMin("0.0") BigDecimal targetSodium,
        @DecimalMin("0.0") BigDecimal targetSugar
) {
}
