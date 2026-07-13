package top.egon.mario.nutrition.dto.response;

/**
 * Ordered recipe cooking-step response DTO.
 */
public record RecipeStepResponse(
        Long id,
        Long recipeId,
        int stepNo,
        String title,
        String instruction
) {
}
