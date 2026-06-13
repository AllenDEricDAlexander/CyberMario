package top.egon.mario.rbac.service.security;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import top.egon.mario.common.api.TraceContext;
import top.egon.mario.rbac.application.RbacAuthApplication;
import top.egon.mario.rbac.service.RbacException;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Verifies access-token authentication failure handling in the JWT WebFlux filter.
 */
class JwtAuthenticationWebFilterTests {

    private final RbacAuthApplication authApplication = mock(RbacAuthApplication.class);
    private final JwtAuthenticationWebFilter filter = new JwtAuthenticationWebFilter(
            authApplication,
            JsonMapper.builder().findAndAddModules().build()
    );

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
    void keepsNonExpiredBearerTokenFailuresOutOfUnauthorizedRefreshSignal() {
        given(authApplication.authenticateAccessToken("invalid-access"))
                .willThrow(new RbacException("AUTH_TOKEN_INVALID", "access token is inactive"));
        MockServerWebExchange exchange = bearerExchange("invalid-access");
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        StepVerifier.create(filter.filter(exchange, chainExchange -> {
                            chainCalled.set(true);
                            return Mono.empty();
                        })
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-1")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(RbacException.class)
                        .hasMessageContaining("AUTH_TOKEN_INVALID"))
                .verify();

        assertThat(chainCalled).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private MockServerWebExchange bearerExchange(String token) {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build());
    }

}
