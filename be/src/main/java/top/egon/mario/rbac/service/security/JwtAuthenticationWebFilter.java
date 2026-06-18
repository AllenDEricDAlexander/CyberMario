package top.egon.mario.rbac.service.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.TraceContext;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.rbac.application.RbacAuthApplication;
import top.egon.mario.rbac.service.RbacException;

/**
 * Extracts Bearer access tokens and publishes RBAC Authentication into Reactor context.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationWebFilter implements WebFilter {

    public static final String PERMISSION_VERSION_HEADER = "X-Rbac-Permission-Version";
    private final RbacAuthApplication authApplication;
    private final ObjectMapper objectMapper;
    private final Scheduler blockingScheduler;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String token = bearerToken(exchange);
        if (token == null) {
            return chain.filter(exchange);
        }
        LogUtil.debug(log).log("bearer token detected, path={}", exchange.getRequest().getPath().value());
        return Mono.deferContextual(contextView -> {
            String traceId = TraceContext.traceId(contextView);
            return Mono.fromCallable(() -> TraceContext.withMdc(traceId, () -> authApplication.authenticateAccessToken(token)))
                    .subscribeOn(blockingScheduler)
                    .flatMap(authentication -> {
                        writePermissionVersionHeader(exchange, authentication.getPrincipal());
                        return chain.filter(exchange)
                                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(new SecurityContextImpl(authentication))));
                    })
                    .doOnError(error -> TraceContext.withMdc(traceId,
                            () -> LogUtil.warn(log).log("bearer token authentication failed, path={}",
                                    exchange.getRequest().getPath().value())))
                    .onErrorResume(RbacException.class, error -> writeRejectedAccessTokenResponse(exchange, traceId, error));
        });
    }

    private String bearerToken(ServerWebExchange exchange) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring(7);
    }

    private void writePermissionVersionHeader(ServerWebExchange exchange, Object principal) {
        if (principal instanceof RbacPrincipal rbacPrincipal && rbacPrincipal.permissionVersion() != null
                && !rbacPrincipal.permissionVersion().isBlank()) {
            exchange.getResponse().getHeaders().set(PERMISSION_VERSION_HEADER, rbacPrincipal.permissionVersion());
        }
    }

    private Mono<Void> writeRejectedAccessTokenResponse(ServerWebExchange exchange, String traceId, RbacException error) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(ApiResponse.fail(error.getCode(), error.getDetailMessage(), traceId));
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            exchange.getResponse().getHeaders().remove(HttpHeaders.WWW_AUTHENTICATE);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
        } catch (JsonProcessingException ex) {
            return Mono.error(ex);
        }
    }

}
