package top.egon.mario.nutrition.dto.response;

import java.util.List;

/**
 * Publish validation result for a recipe.
 */
public record RecipeValidationResponse(
        boolean publishable,
        List<String> errors,
        List<String> warnings
) {
}
