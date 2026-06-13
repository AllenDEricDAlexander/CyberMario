package top.egon.mario.rbac.service.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import top.egon.mario.common.api.TraceContext;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.rbac.application.RbacAuthApplication;

/**
 * Extracts Bearer access tokens and publishes RBAC Authentication into Reactor context.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationWebFilter implements WebFilter {

    private final RbacAuthApplication authApplication;

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
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnError(error -> TraceContext.withMdc(traceId,
                            () -> LogUtil.warn(log).log("bearer token authentication failed, path={}",
                                    exchange.getRequest().getPath().value())))
                    .flatMap(authentication -> chain.filter(exchange)
                            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(new SecurityContextImpl(authentication)))));
        });
    }

    private String bearerToken(ServerWebExchange exchange) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring(7);
    }

}
