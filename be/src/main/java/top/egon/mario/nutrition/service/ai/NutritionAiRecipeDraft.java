package top.egon.mario.nutrition.service.ai;

import top.egon.mario.nutrition.po.enums.NutritionMealType;
import top.egon.mario.nutrition.dto.request.RecipeStepRequest;

import java.math.BigDecimal;
import java.util.List;

/**
 * Normalized AI recipe draft used to create review-only meal plan items.
 */
public record NutritionAiRecipeDraft(
        NutritionMealType mealType,
        Long existingRecipeId,
        String name,
        BigDecimal servingCount,
        List<NutritionAiIngredientDraft> ingredients,
        List<RecipeStepRequest> steps,
        String reason
) {

    public NutritionAiRecipeDraft {
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
