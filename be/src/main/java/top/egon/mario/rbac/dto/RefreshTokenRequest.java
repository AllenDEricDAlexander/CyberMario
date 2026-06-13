package top.egon.mario.rbac.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Refresh request carrying a refresh token.
 */
public record RefreshTokenRequest(@NotBlank String refreshToken) {
}
