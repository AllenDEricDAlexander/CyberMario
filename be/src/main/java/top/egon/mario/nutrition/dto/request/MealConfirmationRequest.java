package top.egon.mario.nutrition.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for confirming one member's meal plan participation.
 */
public record MealConfirmationRequest(
        @NotNull @Min(1) Long memberProfileId,
        @NotNull Boolean eatAtHome,
        List<@Valid MealConfirmationItemRequest> items,
        @Size(max = 512) String remark
) {

    public MealConfirmationRequest {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
