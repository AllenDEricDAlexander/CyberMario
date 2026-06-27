package top.egon.mario.rbac.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Account number or email/password login request.
 */
public record LoginRequest(
        @NotBlank String account,
        @NotBlank String password
) {
}
