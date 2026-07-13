package top.egon.mario.nutrition.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for authorizing a user as a member profile guardian.
 */
public record AssignProfileGuardianRequest(
        @NotNull @Min(1) Long userId
) {
}
