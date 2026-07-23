package top.egon.mario.rbac.service.security;

import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.stream.Stream;

/**
 * Defines the small set of endpoints that may bypass RBAC API authority checks.
 */
public final class RbacPublicApiPolicy {

    public static final String[] PUBLIC_AUTH_ENDPOINTS = {
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh"
    };
    public static final String[] PUBLIC_AUTH_CSRF_ENDPOINTS = {
            "/api/auth/csrf",
            "/api/auth/password-key"
    };
    public static final String[] PUBLIC_ACTIVATION_ENDPOINTS = {
            "/api/auth/activation/complete"
    };
    public static final String[] PUBLIC_ACTUATOR_ENDPOINTS = {
            "/actuator/health/**",
            "/actuator/info"
    };
    public static final String[] PUBLIC_WEBSOCKET_ENDPOINTS = {
            "/ws/im"
    };
    public static final String[] PUBLIC_EXTERNAL_IM_WEBHOOK_ENDPOINTS = {
            "/api/external-im/webhooks/**"
    };

    private static final Set<String> PUBLIC_POST_PATHS = Set.copyOf(Stream.concat(
            Stream.of(PUBLIC_AUTH_ENDPOINTS), Stream.of(PUBLIC_ACTIVATION_ENDPOINTS)).toList());
    private static final Set<String> PUBLIC_GET_PATHS = Set.of("/api/auth/csrf", "/api/auth/password-key",
            "/actuator/health", "/actuator/info", "/ws/im");

    private RbacPublicApiPolicy() {
    }

    public static boolean isAllowedPublicRule(String method, String urlPattern) {
        if (!StringUtils.hasText(method) || !StringUtils.hasText(urlPattern)) {
            return false;
        }
        String normalizedMethod = method.trim().toUpperCase();
        String normalizedPattern = urlPattern.trim();
        if ("POST".equals(normalizedMethod)) {
            return PUBLIC_POST_PATHS.contains(normalizedPattern)
                    || normalizedPattern.startsWith("/api/external-im/webhooks/");
        }
        if ("GET".equals(normalizedMethod)) {
            return PUBLIC_GET_PATHS.contains(normalizedPattern) || isHealthSubPath(normalizedPattern);
        }
        return false;
    }

    private static boolean isHealthSubPath(String urlPattern) {
        return "/actuator/health/**".equals(urlPattern) || urlPattern.startsWith("/actuator/health/");
    }

}
