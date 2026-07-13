package top.egon.mario.nutrition.service.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.nutrition.dto.request.CreateRecipeRequest;
import top.egon.mario.nutrition.dto.request.RecipeIngredientRequest;
import top.egon.mario.nutrition.dto.response.RecipeResponse;
import top.egon.mario.nutrition.dto.response.RecipeValidationResponse;
import top.egon.mario.nutrition.service.NutritionException;
import top.egon.mario.nutrition.service.RecipeService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts untrusted AI candidates into visible, validated recipe entities.
 */
@Service
@RequiredArgsConstructor
public class NutritionAiRecipeMaterializationService {

    private final RecipeService recipeService;

    @Transactional
    public List<MaterializedNutritionRecipe> materialize(
            Long familyId, NutritionAiMenuDraft draft, Long actorId) {
        if (draft == null || draft.recipes().isEmpty()) {
            throw invalid("AI recommendation recipes are required");
        }
        List<MaterializedNutritionRecipe> results = new ArrayList<>();
        for (NutritionAiRecipeDraft candidate : draft.recipes()) {
            results.add(materializeRecipe(familyId, draft, candidate, actorId));
        }
        return List.copyOf(results);
    }

    private MaterializedNutritionRecipe materializeRecipe(
            Long familyId, NutritionAiMenuDraft menu, NutritionAiRecipeDraft candidate, Long actorId) {
        if (candidate == null || candidate.mealType() == null || !menu.mealTypes().contains(candidate.mealType())) {
            throw invalid("AI recommendation recipe meal type is invalid");
        }
        boolean existing = candidate.existingRecipeId() != null;
        boolean generated = StringUtils.hasText(candidate.name()) || !candidate.ingredients().isEmpty()
                || !candidate.steps().isEmpty();
        if (existing == generated) {
            throw invalid("AI recipe must contain exactly one existing id or generated body");
        }
        RecipeResponse recipe;
        if (existing) {
            recipe = recipeService.getRecipe(familyId, candidate.existingRecipeId(), actorId);
        } else {
            int servingCount = integerServingCount(candidate.servingCount());
            List<RecipeIngredientRequest> ingredients = candidate.ingredients().stream()
                    .map(this::toIngredientRequest)
                    .toList();
            recipe = recipeService.createAiGeneratedRecipe(familyId, new CreateRecipeRequest(
                    candidate.name(), candidate.mealType().name(), candidate.reason(), servingCount,
                    null, null, List.of(), List.of(), ingredients, candidate.steps()), actorId);
        }
        RecipeValidationResponse validation = recipeService.validateRecipe(familyId, recipe.id(), actorId);
        if (!validation.publishable()) {
            throw new NutritionException(
                    "NUTRITION_AI_RECIPE_INVALID", "AI recipe cannot be published: " + validation.errors());
        }
        BigDecimal servingCount = candidate.servingCount() == null
                ? BigDecimal.valueOf(recipe.servingCount()) : candidate.servingCount();
        if (servingCount.signum() <= 0) {
            throw invalid("AI recommendation serving count must be positive");
        }
        return new MaterializedNutritionRecipe(
                recipe.id(), candidate.mealType(), recipe.name(), servingCount,
                recipe.nutritionSnapshot(), recipe.estimatedCost());
    }

    private RecipeIngredientRequest toIngredientRequest(NutritionAiIngredientDraft ingredient) {
        if (ingredient == null || !StringUtils.hasText(ingredient.foodName())
                || ingredient.amount() == null || ingredient.amount().signum() <= 0
                || !StringUtils.hasText(ingredient.unit())) {
            throw invalid("AI recommendation ingredient is invalid");
        }
        return new RecipeIngredientRequest(
                ingredient.standardFoodId(), ingredient.foodName(), ingredient.category(), ingredient.amount(),
                ingredient.unit(), ingredient.gramsPerUnit(), ingredient.optional());
    }

    private int integerServingCount(BigDecimal servingCount) {
        BigDecimal value = servingCount == null ? BigDecimal.ONE : servingCount;
        try {
            int result = value.intValueExact();
            if (result <= 0) {
                throw new ArithmeticException();
            }
            return result;
        } catch (ArithmeticException ex) {
            throw invalid("generated AI recipe serving count must be a positive integer");
        }
    }

    private NutritionException invalid(String message) {
        return new NutritionException("NUTRITION_AI_OUTPUT_INVALID", message);
    }
}
