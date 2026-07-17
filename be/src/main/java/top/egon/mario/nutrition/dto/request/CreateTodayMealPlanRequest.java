package top.egon.mario.nutrition.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Request for creating one manually curated meal plan for today.
 */
public record CreateTodayMealPlanRequest(
        @NotBlank @Size(max = 128) String title,
        @NotNull Instant confirmationCutoffAt,
        @NotEmpty List<@Valid MealPlanItemRequest> items
) {

    public CreateTodayMealPlanRequest {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
