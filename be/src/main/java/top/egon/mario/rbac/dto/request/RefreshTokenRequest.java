package top.egon.mario.rbac.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Refresh request carrying a refresh token.
 */
public record RefreshTokenRequest(@NotBlank String refreshToken) {
}
