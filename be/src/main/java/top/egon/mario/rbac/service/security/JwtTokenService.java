package top.egon.mario.rbac.service.security;

/**
 * Signs, verifies and hashes RBAC JWT tokens.
 */
public interface JwtTokenService {

    JwtTokenPair createTokenPair(Long userId, String username);

    JwtClaims validate(String token, String expectedType);

    String hashToken(String token);

}
