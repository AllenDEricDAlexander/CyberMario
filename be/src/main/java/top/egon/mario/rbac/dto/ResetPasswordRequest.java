package top.egon.mario.rbac.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Administrator password reset request.
 */
public record ResetPasswordRequest(@NotBlank String password) {
}
