package top.egon.mario.nutrition.service.ai;

import top.egon.mario.nutrition.po.enums.NutritionMealType;

import java.math.BigDecimal;
import java.util.List;

/**
 * Normalized AI menu draft stored for review before any meal plan can be published.
 */
public record NutritionAiMenuDraft(
        String title,
        String reason,
        List<NutritionMealType> mealTypes,
        List<NutritionAiRecipeDraft> recipes,
        BigDecimal costEstimate
) {

    public NutritionAiMenuDraft {
        mealTypes = mealTypes == null ? List.of() : List.copyOf(mealTypes);
        recipes = recipes == null ? List.of() : List.copyOf(recipes);
    }
}
