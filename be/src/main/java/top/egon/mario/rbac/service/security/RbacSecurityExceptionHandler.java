package top.egon.mario.rbac.service.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.web.server.csrf.CsrfException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.TraceContext;

/**
 * Writes standard API responses for Spring Security authentication and authorization failures.
 */
@Component
@RequiredArgsConstructor
public class RbacSecurityExceptionHandler implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {

    private static final String AUTH_UNAUTHENTICATED = "AUTH_UNAUTHENTICATED";
    private static final String AUTH_FORBIDDEN = "AUTH_FORBIDDEN";
    private static final String AUTH_CSRF_INVALID = "AUTH_CSRF_INVALID";
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        return Mono.deferContextual(contextView -> writeResponse(exchange, HttpStatus.UNAUTHORIZED,
                AUTH_UNAUTHENTICATED, "authentication is required", TraceContext.traceId(contextView)));
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        if (denied instanceof CsrfException) {
            return Mono.deferContextual(contextView -> writeResponse(exchange, HttpStatus.FORBIDDEN,
                    AUTH_CSRF_INVALID, "csrf token is invalid", TraceContext.traceId(contextView)));
        }
        return Mono.deferContextual(contextView -> writeResponse(exchange, HttpStatus.FORBIDDEN,
                AUTH_FORBIDDEN, "access is denied", TraceContext.traceId(contextView)));
    }

    private Mono<Void> writeResponse(ServerWebExchange exchange, HttpStatus status, String code, String message, String traceId) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(ApiResponse.fail(code, message, traceId));
            exchange.getResponse().setStatusCode(status);
            exchange.getResponse().getHeaders().remove(HttpHeaders.WWW_AUTHENTICATE);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
        } catch (JsonProcessingException ex) {
            return Mono.error(ex);
        }
    }

}
