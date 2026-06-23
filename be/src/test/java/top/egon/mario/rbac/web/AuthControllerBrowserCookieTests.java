package top.egon.mario.rbac.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import top.egon.mario.rbac.application.RbacAuthApplication;
import top.egon.mario.rbac.dto.request.LoginRequest;
import top.egon.mario.rbac.dto.request.RefreshTokenRequest;
import top.egon.mario.rbac.dto.request.RegisterRequest;
import top.egon.mario.rbac.dto.response.LoginResponse;
import top.egon.mario.rbac.dto.response.MenuTreeResponse;
import top.egon.mario.rbac.dto.response.UserResponse;
import top.egon.mario.rbac.service.security.BrowserAuthCookieProperties;
import top.egon.mario.rbac.service.security.BrowserAuthCookieService;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies auth endpoints preserve API-token compatibility while supporting browser auth cookies.
 */
class AuthControllerBrowserCookieTests {

    private RbacAuthApplication authApplication;
    private BrowserAuthCookieService browserAuthCookieService;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        authApplication = mock(RbacAuthApplication.class);
        BrowserAuthCookieProperties properties = new BrowserAuthCookieProperties();
        properties.setSecure(false);
        browserAuthCookieService = new BrowserAuthCookieService(properties);
        controller = new AuthController(authApplication, browserAuthCookieService);
        controller.setBlockingScheduler(Schedulers.immediate());
    }

    @Test
    void browserLoginWritesCookiesAndHidesBodyTokens() {
        LoginRequest request = new LoginRequest("mario", "secret");
        when(authApplication.login(eq(request), nullable(String.class), nullable(String.class)))
                .thenReturn(loginResponse("access-token", "refresh-token"));
        MockServerWebExchange exchange = exchange("/api/auth/login", true);

        StepVerifier.create(controller.login(request, exchange))
                .assertNext(response -> {
                    assertThat(response.data().accessToken()).isNull();
                    assertThat(response.data().refreshToken()).isNull();
                    assertTokenCookies(exchange, "access-token", "refresh-token");
                })
                .verifyComplete();
    }

    @Test
    void browserRegisterWritesCookiesAndHidesBodyTokens() {
        RegisterRequest request = new RegisterRequest("mario", "password123", "Mario",
                "mario@example.com", "13800000000", null);
        when(authApplication.register(eq(request), nullable(String.class), nullable(String.class)))
                .thenReturn(loginResponse("register-access-token", "register-refresh-token"));
        MockServerWebExchange exchange = exchange("/api/auth/register", true);

        StepVerifier.create(controller.register(request, exchange))
                .assertNext(response -> {
                    assertThat(response.data().accessToken()).isNull();
                    assertThat(response.data().refreshToken()).isNull();
                    assertTokenCookies(exchange, "register-access-token", "register-refresh-token");
                })
                .verifyComplete();
    }

    @Test
    void nonBrowserLoginReturnsBodyTokensAndDoesNotWriteAuthCookies() {
        LoginRequest request = new LoginRequest("mario", "secret");
        when(authApplication.login(eq(request), nullable(String.class), nullable(String.class)))
                .thenReturn(loginResponse("access-token", "refresh-token"));
        MockServerWebExchange exchange = exchange("/api/auth/login", false);

        StepVerifier.create(controller.login(request, exchange))
                .assertNext(response -> {
                    assertThat(response.data().accessToken()).isEqualTo("access-token");
                    assertThat(response.data().refreshToken()).isEqualTo("refresh-token");
                    assertNoAuthCookies(exchange);
                })
                .verifyComplete();
    }

    @Test
    void browserRefreshReadsRefreshCookieWritesCookiesAndHidesBodyTokens() {
        when(authApplication.refresh(eq("cookie-refresh-token"), nullable(String.class), nullable(String.class)))
                .thenReturn(loginResponse("new-access-token", "new-refresh-token"));
        MockServerWebExchange exchange = tokenCookieExchange("/api/auth/refresh", true, "cookie-refresh-token");

        StepVerifier.create(controller.refresh(null, exchange))
                .assertNext(response -> {
                    assertThat(response.data().accessToken()).isNull();
                    assertThat(response.data().refreshToken()).isNull();
                    assertTokenCookies(exchange, "new-access-token", "new-refresh-token");
                })
                .verifyComplete();
        verify(authApplication).refresh(eq("cookie-refresh-token"), nullable(String.class), nullable(String.class));
    }

    @Test
    void nonBrowserRefreshReadsBodyAndReturnsBodyTokens() {
        RefreshTokenRequest request = new RefreshTokenRequest("body-refresh-token");
        when(authApplication.refresh(eq("body-refresh-token"), nullable(String.class), nullable(String.class)))
                .thenReturn(loginResponse("new-access-token", "new-refresh-token"));
        MockServerWebExchange exchange = exchange("/api/auth/refresh", false);

        StepVerifier.create(controller.refresh(request, exchange))
                .assertNext(response -> {
                    assertThat(response.data().accessToken()).isEqualTo("new-access-token");
                    assertThat(response.data().refreshToken()).isEqualTo("new-refresh-token");
                    assertNoAuthCookies(exchange);
                })
                .verifyComplete();
        verify(authApplication).refresh(eq("body-refresh-token"), nullable(String.class), nullable(String.class));
    }

    @Test
    void browserLogoutReadsRefreshCookieAndClearsCookies() {
        MockServerWebExchange exchange = tokenCookieExchange("/api/auth/logout", true, "cookie-refresh-token");

        StepVerifier.create(controller.logout(null, exchange))
                .assertNext(response -> {
                    assertThat(response.data()).isNull();
                    assertExpiredAuthCookies(exchange);
                })
                .verifyComplete();
        verify(authApplication).logout("cookie-refresh-token");
    }

    @Test
    void browserLogoutWithoutRefreshCookieClearsCookiesAndSkipsApplicationLogout() {
        MockServerWebExchange exchange = exchange("/api/auth/logout", true);

        StepVerifier.create(controller.logout(null, exchange))
                .assertNext(response -> {
                    assertThat(response.data()).isNull();
                    assertExpiredAuthCookies(exchange);
                })
                .verifyComplete();
        verify(authApplication, never()).logout(nullable(String.class));
    }

    @Test
    void nonBrowserLogoutReadsBodyAndDoesNotClearCookies() {
        RefreshTokenRequest request = new RefreshTokenRequest("body-refresh-token");
        MockServerWebExchange exchange = exchange("/api/auth/logout", false);

        StepVerifier.create(controller.logout(request, exchange))
                .assertNext(response -> {
                    assertThat(response.data()).isNull();
                    assertNoAuthCookies(exchange);
                })
                .verifyComplete();
        verify(authApplication).logout("body-refresh-token");
        verify(authApplication, never()).logout("cookie-refresh-token");
    }

    private MockServerWebExchange exchange(String path, boolean browserHeader) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.post(path)
                .header("User-Agent", "controller-test");
        if (browserHeader) {
            request.header("X-Client-Type", "browser");
        }
        return MockServerWebExchange.from(request.build());
    }

    private MockServerWebExchange tokenCookieExchange(String path, boolean browserHeader, String refreshToken) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.post(path)
                .header("User-Agent", "controller-test")
                .cookie(new HttpCookie("CM_REFRESH_TOKEN", refreshToken));
        if (browserHeader) {
            request.header("X-Client-Type", "browser");
        }
        return MockServerWebExchange.from(request.build());
    }

    private void assertTokenCookies(MockServerWebExchange exchange, String accessToken, String refreshToken) {
        ResponseCookie accessCookie = exchange.getResponse().getCookies().getFirst("CM_ACCESS_TOKEN");
        ResponseCookie refreshCookie = exchange.getResponse().getCookies().getFirst("CM_REFRESH_TOKEN");
        assertThat(accessCookie).isNotNull();
        assertThat(accessCookie.getValue()).isEqualTo(accessToken);
        assertThat(accessCookie.getPath()).isEqualTo("/api");
        assertThat(accessCookie.getMaxAge()).isEqualTo(Duration.ofSeconds(1800));
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.getValue()).isEqualTo(refreshToken);
        assertThat(refreshCookie.getPath()).isEqualTo("/api/auth");
        assertThat(refreshCookie.getMaxAge()).isEqualTo(Duration.ofSeconds(1209600));
    }

    private void assertExpiredAuthCookies(MockServerWebExchange exchange) {
        ResponseCookie accessCookie = exchange.getResponse().getCookies().getFirst("CM_ACCESS_TOKEN");
        ResponseCookie refreshCookie = exchange.getResponse().getCookies().getFirst("CM_REFRESH_TOKEN");
        assertThat(accessCookie).isNotNull();
        assertThat(accessCookie.getValue()).isEmpty();
        assertThat(accessCookie.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.getValue()).isEmpty();
        assertThat(refreshCookie.getMaxAge()).isEqualTo(Duration.ZERO);
    }

    private void assertNoAuthCookies(MockServerWebExchange exchange) {
        assertThat(exchange.getResponse().getCookies().getFirst("CM_ACCESS_TOKEN")).isNull();
        assertThat(exchange.getResponse().getCookies().getFirst("CM_REFRESH_TOKEN")).isNull();
    }

    private LoginResponse loginResponse(String accessToken, String refreshToken) {
        UserResponse user = new UserResponse();
        user.setId(1L);
        user.setUsername("mario");
        MenuTreeResponse menu = new MenuTreeResponse();
        menu.setPermCode("menu:dashboard");
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
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
