package top.egon.mario.nutrition.dto.response;

import top.egon.mario.nutrition.po.enums.NutritionRecipeSourceType;
import top.egon.mario.nutrition.po.enums.NutritionStatus;

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
        NutritionStatus status,
        List<RecipeIngredientResponse> ingredients,
        Instant createdAt,
        Instant updatedAt
) {
}
