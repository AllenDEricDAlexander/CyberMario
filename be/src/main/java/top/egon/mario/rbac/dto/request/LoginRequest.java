package top.egon.mario.rbac.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Username/password login request.
 */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
) {
}
