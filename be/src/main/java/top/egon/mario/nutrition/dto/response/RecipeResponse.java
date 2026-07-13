package top.egon.mario.nutrition.dto.response;

import top.egon.mario.nutrition.po.enums.NutritionRecipeSourceType;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.service.calculation.NutritionTotals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Family recipe response DTO.
 */
public record RecipeResponse(
        Long id,
        Long familyId,
        NutritionRecipeSourceType sourceType,
        String name,
        String category,
        String description,
        int servingCount,
        Integer cookingMinutes,
        String difficultyLevel,
        List<String> suitableTags,
        List<String> allergenTags,
        NutritionTotals nutritionSnapshot,
        BigDecimal estimatedCost,
        NutritionStatus status,
        List<RecipeIngredientResponse> ingredients,
        List<RecipeStepResponse> steps,
        Instant createdAt,
        Instant updatedAt
) {
}
