package top.egon.mario.nutrition.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/**
 * Versioned request for replacing the editable meal-plan item set.
 */
public record UpdateMealPlanRequest(
        @NotNull Long expectedVersion,
        Instant confirmationCutoffAt,
        @NotEmpty List<@Valid MealPlanItemRequest> items
) {

    public UpdateMealPlanRequest {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
