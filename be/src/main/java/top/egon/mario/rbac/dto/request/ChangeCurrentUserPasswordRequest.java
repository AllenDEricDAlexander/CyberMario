package top.egon.mario.rbac.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Current-user request for changing the account password.
 */
public record ChangeCurrentUserPasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8, max = 128) String newPassword,
        @NotBlank String confirmPassword
) {
}
