package top.egon.mario.nutrition.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Versioned adjustment of final servings after meal confirmation closes.
 */
public record AdjustConfirmedMenuRequest(
        @NotNull Long expectedVersion,
        @Size(max = 512) String note,
        @NotEmpty List<@Valid ConfirmedMenuItemAdjustmentRequest> items
) {

    public AdjustConfirmedMenuRequest {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
