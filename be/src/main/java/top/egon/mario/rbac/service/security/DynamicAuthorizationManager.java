package top.egon.mario.rbac.service.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import top.egon.mario.common.api.TraceContext;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.rbac.service.model.ApiPermissionRule;

/**
 * Authorizes WebFlux requests by matching configured API permissions.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DynamicAuthorizationManager implements ReactiveAuthorizationManager<AuthorizationContext> {

    private final RbacApiRuleCache apiRuleCache;

    @Override
    public Mono<AuthorizationDecision> check(Mono<Authentication> authentication, AuthorizationContext context) {
        String method = context.getExchange().getRequest().getMethod().name();
        String path = context.getExchange().getRequest().getPath().pathWithinApplication().value();
        return Mono.deferContextual(contextView -> {
            String traceId = TraceContext.traceId(contextView);
            return Mono.fromCallable(() -> TraceContext.withMdc(traceId, () -> apiRuleCache.match(method, path)))
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnNext(rule -> TraceContext.withMdc(traceId, () -> {
                        LogUtil.debug(log).log("rbac api rule match evaluated, method={}, path={}, matched={}",
                                method, path, rule.isPresent());
                    }))
                    .flatMap(Mono::justOrEmpty)
                    .flatMap(rule -> decide(authentication, rule))
                    .defaultIfEmpty(new AuthorizationDecision(false));
        });
    }

    private Mono<AuthorizationDecision> decide(Mono<Authentication> authentication, ApiPermissionRule rule) {
        if (rule.publicFlag()) {
            return Mono.just(new AuthorizationDecision(true));
        }
        return authentication
                .filter(Authentication::isAuthenticated)
                .map(auth -> auth.getAuthorities().stream()
                        .anyMatch(authority -> authority.getAuthority().equals(rule.permissionCode())))
                .map(AuthorizationDecision::new)
                .defaultIfEmpty(new AuthorizationDecision(false));
    }

}
