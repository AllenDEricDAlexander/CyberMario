package top.egon.mario.rbac.service.security;

/**
 * Access and refresh token pair.
 */
public record JwtTokenPair(
        String accessToken,
        String refreshToken,
        String accessTokenId,
        String refreshTokenId,
        long accessTokenExpiresInSeconds,
        long refreshTokenExpiresInSeconds
) {
}
