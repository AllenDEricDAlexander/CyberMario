package top.egon.mario.rbac.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Public request containing an activation token and encrypted initial password.
 */
public record CompleteAccountActivationRequest(
        @NotBlank String token,
        @NotBlank String passwordKeyId,
        @NotBlank String encryptedPassword
) {
}
