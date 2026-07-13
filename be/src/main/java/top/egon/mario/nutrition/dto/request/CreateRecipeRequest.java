package top.egon.mario.nutrition.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for creating a family recipe.
 */
public record CreateRecipeRequest(
        @NotBlank @Size(max = 128) String name,
        @Size(max = 64) String category,
        @Size(max = 2048) String description,
        @Min(1) Integer servingCount,
        @Min(0) Integer cookingMinutes,
        @Size(max = 32) String difficultyLevel,
        List<@NotBlank @Size(max = 128) String> suitableTags,
        List<@NotBlank @Size(max = 128) String> allergenTags,
        @NotEmpty List<@Valid RecipeIngredientRequest> ingredients,
        List<@Valid RecipeStepRequest> steps
) {

    public CreateRecipeRequest(String name, String category, String description, Integer servingCount,
                               List<RecipeIngredientRequest> ingredients) {
        this(name, category, description, servingCount, null, null,
                List.of(), List.of(), ingredients, List.of());
    }
}
