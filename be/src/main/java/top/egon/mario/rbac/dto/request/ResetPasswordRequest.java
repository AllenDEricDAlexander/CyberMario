package top.egon.mario.rbac.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Administrator password reset request.
 */
public record ResetPasswordRequest(@NotBlank String password) {
}
