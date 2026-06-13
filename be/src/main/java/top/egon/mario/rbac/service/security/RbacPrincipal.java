package top.egon.mario.rbac.service.security;

import java.util.Set;

/**
 * Authenticated RBAC principal stored in Spring Security.
 */
public record RbacPrincipal(
        Long userId,
        String username,
        Set<String> roleCodes,
        Set<String> apiAuthorities
) {
}
