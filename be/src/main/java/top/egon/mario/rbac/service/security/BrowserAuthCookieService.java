package top.egon.mario.rbac.service.security;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import top.egon.mario.rbac.dto.response.LoginResponse;

import java.time.Duration;

/**
 * Writes and reads browser auth cookies while keeping body tokens optional.
 */
@Component
@RequiredArgsConstructor
public class BrowserAuthCookieService {

    private static final String LEGACY_ACCESS_COOKIE_PATH = "/api";

    private final BrowserAuthCookieProperties properties;

    public boolean isBrowserClient(ServerWebExchange exchange) {
        if (!properties.isEnabled()) {
            return false;
        }
        String clientType = exchange.getRequest().getHeaders().getFirst(properties.getBrowserHeaderName());
        return clientType != null && clientType.equalsIgnoreCase(properties.getBrowserHeaderValue());
    }

    public void writeTokenCookies(ServerWebExchange exchange, LoginResponse response) {
        if (!properties.isEnabled()) {
            return;
        }
        writeCookie(
                exchange,
                properties.getAccessCookieName(),
                response.accessToken(),
                properties.getAccessCookiePath(),
                Duration.ofSeconds(response.accessTokenExpiresInSeconds())
        );
        expireLegacyAccessCookie(exchange);
        writeCookie(
                exchange,
                properties.getRefreshCookieName(),
                response.refreshToken(),
                properties.getRefreshCookiePath(),
                Duration.ofSeconds(response.refreshTokenExpiresInSeconds())
        );
    }

    public String readAccessToken(ServerWebExchange exchange) {
        if (!isBrowserClient(exchange)) {
            return null;
        }
        return readCookie(exchange, properties.getAccessCookieName());
    }

    public String readRefreshToken(ServerWebExchange exchange) {
        if (!isBrowserClient(exchange)) {
            return null;
        }
        return readCookie(exchange, properties.getRefreshCookieName());
    }

    public void clearTokenCookies(ServerWebExchange exchange) {
        if (!properties.isEnabled()) {
            return;
        }
        expireCookie(exchange, properties.getAccessCookieName(), properties.getAccessCookiePath());
        expireLegacyAccessCookie(exchange);
        expireCookie(exchange, properties.getRefreshCookieName(), properties.getRefreshCookiePath());
    }

    public LoginResponse withoutBodyTokens(LoginResponse response) {
        return LoginResponse.builder()
                .accessToken(null)
                .refreshToken(null)
                .accessTokenExpiresInSeconds(response.accessTokenExpiresInSeconds())
                .refreshTokenExpiresInSeconds(response.refreshTokenExpiresInSeconds())
                .user(response.user())
                .roleCodes(response.roleCodes())
                .menus(response.menus())
                .buttonCodes(response.buttonCodes())
                .permissionCodes(response.permissionCodes())
                .permissionVersion(response.permissionVersion())
                .build();
    }

    private void writeCookie(ServerWebExchange exchange, String name, String value, String path, Duration maxAge) {
        exchange.getResponse().addCookie(ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.isSecure())
                .sameSite(properties.getSameSite())
                .path(path)
                .maxAge(maxAge)
                .build());
    }

    private String readCookie(ServerWebExchange exchange, String name) {
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(name);
        return cookie == null ? null : cookie.getValue();
    }

    private void expireCookie(ServerWebExchange exchange, String name, String path) {
        exchange.getResponse().addCookie(ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(properties.isSecure())
                .sameSite(properties.getSameSite())
                .path(path)
                .maxAge(Duration.ZERO)
                .build());
    }

    private void expireLegacyAccessCookie(ServerWebExchange exchange) {
        if (!LEGACY_ACCESS_COOKIE_PATH.equals(properties.getAccessCookiePath())) {
            expireCookie(exchange, properties.getAccessCookieName(), LEGACY_ACCESS_COOKIE_PATH);
        }
    }

}
