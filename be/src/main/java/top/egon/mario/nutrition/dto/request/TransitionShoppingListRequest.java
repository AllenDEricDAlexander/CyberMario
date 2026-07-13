package top.egon.mario.nutrition.dto.request;

import jakarta.validation.constraints.NotNull;
import top.egon.mario.nutrition.po.enums.NutritionShoppingListStatus;

/**
 * Request body for an explicit shopping-list lifecycle transition.
 */
public record TransitionShoppingListRequest(
        @NotNull NutritionShoppingListStatus targetStatus
) {
}
