package top.egon.mario.nutrition.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for binding a login user to a family member profile.
 */
public record BindMemberUserRequest(
        @NotNull @Min(1) Long userId
) {
}
