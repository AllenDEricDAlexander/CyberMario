package top.egon.mario.rbac.service.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import top.egon.mario.rbac.dto.response.LoginResponse;
import top.egon.mario.rbac.dto.response.MenuTreeResponse;
import top.egon.mario.rbac.dto.response.UserResponse;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies browser auth token cookie handling without exposing tokens in the response body.
 */
class BrowserAuthCookieServiceTests {

    private final BrowserAuthCookieProperties properties = new BrowserAuthCookieProperties();
    private final BrowserAuthCookieService service = new BrowserAuthCookieService(properties);

    BrowserAuthCookieServiceTests() {
        properties.setSecure(false);
    }

    @Test
    void detectsBrowserClientByConfiguredHeader() {
        MockServerWebExchange browserExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/auth/login")
                .header("X-Client-Type", "BROWSER")
                .build());
        MockServerWebExchange apiExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/auth/login")
                .build());

        assertThat(service.isBrowserClient(browserExchange)).isTrue();
        assertThat(service.isBrowserClient(apiExchange)).isFalse();
    }

    @Test
    void writesAccessAndRefreshTokenCookies() {
        MockServerWebExchange exchange = exchange();

        service.writeTokenCookies(exchange, loginResponse());

        ResponseCookie accessCookie = exchange.getResponse().getCookies().getFirst("CM_ACCESS_TOKEN");
        ResponseCookie refreshCookie = exchange.getResponse().getCookies().getFirst("CM_REFRESH_TOKEN");
        assertThat(accessCookie).isNotNull();
        assertThat(accessCookie.getValue()).isEqualTo("access-token");
        assertThat(accessCookie.isHttpOnly()).isTrue();
        assertThat(accessCookie.isSecure()).isFalse();
        assertThat(accessCookie.getPath()).isEqualTo("/api");
        assertThat(accessCookie.getMaxAge()).isEqualTo(Duration.ofSeconds(1800));
        assertThat(accessCookie.getSameSite()).isEqualTo("Lax");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.getValue()).isEqualTo("refresh-token");
        assertThat(refreshCookie.isHttpOnly()).isTrue();
        assertThat(refreshCookie.isSecure()).isFalse();
        assertThat(refreshCookie.getPath()).isEqualTo("/api/auth");
        assertThat(refreshCookie.getMaxAge()).isEqualTo(Duration.ofSeconds(1209600));
        assertThat(refreshCookie.getSameSite()).isEqualTo("Lax");
    }

    @Test
    void redactsTokensFromBrowserResponseBody() {
        LoginResponse response = loginResponse();

        LoginResponse redacted = service.withoutBodyTokens(response);

        assertThat(redacted.accessToken()).isNull();
        assertThat(redacted.refreshToken()).isNull();
        assertThat(redacted.accessTokenExpiresInSeconds()).isEqualTo(response.accessTokenExpiresInSeconds());
        assertThat(redacted.refreshTokenExpiresInSeconds()).isEqualTo(response.refreshTokenExpiresInSeconds());
        assertThat(redacted.user()).isSameAs(response.user());
        assertThat(redacted.roleCodes()).isEqualTo(response.roleCodes());
        assertThat(redacted.menus()).isEqualTo(response.menus());
        assertThat(redacted.buttonCodes()).isEqualTo(response.buttonCodes());
        assertThat(redacted.permissionCodes()).isEqualTo(response.permissionCodes());
        assertThat(redacted.permissionVersion()).isEqualTo(response.permissionVersion());
    }

    @Test
    void clearsAccessAndRefreshTokenCookies() {
        MockServerWebExchange exchange = exchange();

        service.clearTokenCookies(exchange);

        ResponseCookie accessCookie = exchange.getResponse().getCookies().getFirst("CM_ACCESS_TOKEN");
        ResponseCookie refreshCookie = exchange.getResponse().getCookies().getFirst("CM_REFRESH_TOKEN");
        assertThat(accessCookie).isNotNull();
        assertThat(accessCookie.getValue()).isEmpty();
        assertThat(accessCookie.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(accessCookie.isHttpOnly()).isTrue();
        assertThat(accessCookie.getPath()).isEqualTo("/api");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.getValue()).isEmpty();
        assertThat(refreshCookie.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(refreshCookie.isHttpOnly()).isTrue();
        assertThat(refreshCookie.getPath()).isEqualTo("/api/auth");
    }

    private MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/auth/login").build());
    }

    private LoginResponse loginResponse() {
        UserResponse user = new UserResponse();
        user.setId(1L);
        user.setUsername("mario");
        MenuTreeResponse menu = new MenuTreeResponse();
        menu.setPermCode("menu:dashboard");
        return LoginResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .accessTokenExpiresInSeconds(1800)
                .refreshTokenExpiresInSeconds(1209600)
                .user(user)
                .roleCodes(Set.of("ROLE_ADMIN"))
                .menus(List.of(menu))
                .buttonCodes(Set.of("button:create"))
                .permissionCodes(Set.of("api:users:list"))
                .permissionVersion("permission-v1")
                .build();
    }

}
