package top.egon.mario.rbac.service.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import top.egon.mario.rbac.service.model.ApiPermissionRule;

/**
 * Authorizes WebFlux requests by matching configured API permissions.
 */
@Component
@RequiredArgsConstructor
public class DynamicAuthorizationManager implements ReactiveAuthorizationManager<AuthorizationContext> {

    private final RbacApiRuleCache apiRuleCache;

    @Override
    public Mono<AuthorizationDecision> check(Mono<Authentication> authentication, AuthorizationContext context) {
        String method = context.getExchange().getRequest().getMethod().name();
        String path = context.getExchange().getRequest().getPath().pathWithinApplication().value();
        return Mono.justOrEmpty(apiRuleCache.match(method, path))
                .flatMap(rule -> decide(authentication, rule))
                .defaultIfEmpty(new AuthorizationDecision(false));
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
