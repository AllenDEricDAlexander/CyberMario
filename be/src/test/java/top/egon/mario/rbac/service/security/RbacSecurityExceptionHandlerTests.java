package top.egon.mario.rbac.service.security;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.web.server.csrf.CsrfException;
import reactor.test.StepVerifier;
import top.egon.mario.common.api.TraceContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies standard JSON responses for Spring Security authentication and authorization exceptions.
 */
class RbacSecurityExceptionHandlerTests {

    private final RbacSecurityExceptionHandler handler = new RbacSecurityExceptionHandler(
            JsonMapper.builder().findAndAddModules().build()
    );

    @Test
    void writesUnauthorizedApiResponseWhenAuthenticationIsMissing() {
        MockServerWebExchange exchange = exchange();

        StepVerifier.create(handler.commence(exchange, new InsufficientAuthenticationException("missing token"))
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-401")))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isNull();
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"code\":\"AUTH_UNAUTHENTICATED\"")
                .contains("\"message\":\"authentication is required\"")
                .contains("\"traceId\":\"trace-401\"");
    }

    @Test
    void writesForbiddenApiResponseWhenAccessIsDenied() {
        MockServerWebExchange exchange = exchange();

        StepVerifier.create(handler.handle(exchange, new AccessDeniedException("denied"))
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-403")))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"code\":\"AUTH_FORBIDDEN\"")
                .contains("\"message\":\"access is denied\"")
                .contains("\"traceId\":\"trace-403\"");
    }

    @Test
    void writesCsrfApiResponseWhenCsrfTokenIsInvalid() {
        MockServerWebExchange exchange = exchange();

        StepVerifier.create(handler.handle(exchange, new CsrfException("invalid csrf token"))
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-csrf")))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"code\":\"AUTH_CSRF_INVALID\"")
                .contains("\"message\":\"csrf token is invalid\"")
                .contains("\"traceId\":\"trace-csrf\"");
    }

    private MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/admin/users").build());
    }

}
