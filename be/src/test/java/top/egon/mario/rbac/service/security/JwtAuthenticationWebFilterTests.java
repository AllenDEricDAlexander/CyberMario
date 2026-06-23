package top.egon.mario.rbac.service.security;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import top.egon.mario.common.api.TraceContext;
import top.egon.mario.rbac.application.RbacAuthApplication;
import top.egon.mario.rbac.service.RbacException;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

/**
 * Verifies access-token resolution and authentication failure handling in the JWT WebFlux filter.
 */
class JwtAuthenticationWebFilterTests {

    private final RbacAuthApplication authApplication = mock(RbacAuthApplication.class);
    private final BrowserAuthCookieService browserAuthCookieService =
            new BrowserAuthCookieService(new BrowserAuthCookieProperties());
    private final JwtAuthenticationWebFilter filter = new JwtAuthenticationWebFilter(
            authApplication,
            browserAuthCookieService,
            JsonMapper.builder().findAndAddModules().build(),
            Schedulers.immediate()
    );

    @Test
    void writesPermissionVersionHeaderForAuthenticatedBearerToken() {
        given(authApplication.authenticateAccessToken("valid-access"))
                .willReturn(new UsernamePasswordAuthenticationToken(
                        new RbacPrincipal(1L, "mario", Set.of("ROLE_ADMIN"), Set.of("api:demo"), "permission-v1"),
                        "valid-access",
                        List.of()
                ));
        MockServerWebExchange exchange = bearerExchange("valid-access");
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        StepVerifier.create(filter.filter(exchange, chainExchange -> {
                            chainCalled.set(true);
                            return Mono.empty();
                        })
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-1")))
                .verifyComplete();

        assertThat(chainCalled).isTrue();
        assertThat(exchange.getResponse().getHeaders().getFirst(JwtAuthenticationWebFilter.PERMISSION_VERSION_HEADER))
                .isEqualTo("permission-v1");
    }

    @Test
    void authenticatesAccessTokenFromBrowserCookie() {
        given(authApplication.authenticateAccessToken("valid-cookie-access"))
                .willReturn(new UsernamePasswordAuthenticationToken(
                        new RbacPrincipal(1L, "mario", Set.of("ROLE_ADMIN"), Set.of("api:demo"), "permission-v1"),
                        "valid-cookie-access",
                        List.of()
                ));
        MockServerWebExchange exchange = browserCookieExchange("valid-cookie-access", true);
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        StepVerifier.create(filter.filter(exchange, chainExchange -> {
                            chainCalled.set(true);
                            return Mono.empty();
                        })
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-1")))
                .verifyComplete();

        then(authApplication).should().authenticateAccessToken("valid-cookie-access");
        assertThat(chainCalled).isTrue();
        assertThat(exchange.getResponse().getHeaders().getFirst(JwtAuthenticationWebFilter.PERMISSION_VERSION_HEADER))
                .isEqualTo("permission-v1");
    }

    @Test
    void prefersBearerTokenOverBrowserCookieToken() {
        given(authApplication.authenticateAccessToken("bearer-access"))
                .willReturn(new UsernamePasswordAuthenticationToken(
                        new RbacPrincipal(1L, "mario", Set.of("ROLE_ADMIN"), Set.of("api:demo"), "permission-v1"),
                        "bearer-access",
                        List.of()
                ));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer bearer-access")
                .header("X-Client-Type", "browser")
                .cookie(new HttpCookie("CM_ACCESS_TOKEN", "cookie-access"))
                .build());

        StepVerifier.create(filter.filter(exchange, chainExchange -> Mono.empty())
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-1")))
                .verifyComplete();

        then(authApplication).should().authenticateAccessToken("bearer-access");
        then(authApplication).should(never()).authenticateAccessToken("cookie-access");
    }

    @Test
    void ignoresAccessTokenCookieWithoutBrowserHeader() {
        MockServerWebExchange exchange = browserCookieExchange("cookie-access", false);
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        StepVerifier.create(filter.filter(exchange, chainExchange -> {
                            chainCalled.set(true);
                            return Mono.empty();
                        })
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-1")))
                .verifyComplete();

        assertThat(chainCalled).isTrue();
        then(authApplication).shouldHaveNoInteractions();
    }

    @Test
    void writesUnauthorizedResponseOnlyWhenBearerAccessTokenExpired() {
        given(authApplication.authenticateAccessToken("expired-access"))
                .willThrow(new RbacException("AUTH_TOKEN_EXPIRED", "token expired"));
        MockServerWebExchange exchange = bearerExchange("expired-access");
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        StepVerifier.create(filter.filter(exchange, chainExchange -> {
                            chainCalled.set(true);
                            return Mono.empty();
                        })
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-1")))
                .verifyComplete();

        assertThat(chainCalled).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"code\":\"AUTH_TOKEN_EXPIRED\"")
                .contains("\"traceId\":\"trace-1\"");
    }

    @Test
    void writesUnauthorizedResponseForInvalidBearerTokenWithoutBasicChallenge() {
        given(authApplication.authenticateAccessToken("invalid-access"))
                .willThrow(new RbacException("AUTH_TOKEN_INVALID", "access token is inactive"));
        MockServerWebExchange exchange = bearerExchange("invalid-access");
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        StepVerifier.create(filter.filter(exchange, chainExchange -> {
                            chainCalled.set(true);
                            return Mono.empty();
                        })
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-1")))
                .verifyComplete();

        assertThat(chainCalled).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders()).doesNotContainKey(HttpHeaders.WWW_AUTHENTICATE);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"code\":\"AUTH_TOKEN_INVALID\"")
                .contains("\"message\":\"access token is inactive\"")
                .contains("\"traceId\":\"trace-1\"");
    }

    private MockServerWebExchange bearerExchange(String token) {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build());
    }

    private MockServerWebExchange browserCookieExchange(String token, boolean browserHeader) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get("/api/admin/users")
                .cookie(new HttpCookie("CM_ACCESS_TOKEN", token));
        if (browserHeader) {
            request.header("X-Client-Type", "browser");
        }
        return MockServerWebExchange.from(request.build());
    }

}
