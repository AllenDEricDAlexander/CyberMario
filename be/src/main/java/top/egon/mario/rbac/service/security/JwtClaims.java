package top.egon.mario.rbac.service.security;

import java.time.Instant;

/**
 * Validated JWT claim subset used by RBAC authentication.
 */
public record JwtClaims(
        Long userId,
        String username,
        String tokenId,
        String tokenType,
        Instant expiresAt
) {
}
