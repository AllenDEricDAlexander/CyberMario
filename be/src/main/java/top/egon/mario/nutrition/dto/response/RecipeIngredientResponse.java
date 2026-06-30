package top.egon.mario.nutrition.dto.response;

import java.math.BigDecimal;

/**
 * Family recipe ingredient response DTO.
 */
public record RecipeIngredientResponse(
        Long id,
        Long recipeId,
        Long standardFoodId,
        String rawFoodName,
        BigDecimal amount,
        String unit,
        String mappingStatus,
        boolean optional
) {
}
