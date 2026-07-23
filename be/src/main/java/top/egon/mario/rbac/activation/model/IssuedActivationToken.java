package top.egon.mario.rbac.activation.model;

import org.springframework.security.authentication.ott.OneTimeToken;

/**
 * In-memory-only pairing of a user id and Spring Security one-time token.
 */
public record IssuedActivationToken(Long userId, OneTimeToken token) {
}
