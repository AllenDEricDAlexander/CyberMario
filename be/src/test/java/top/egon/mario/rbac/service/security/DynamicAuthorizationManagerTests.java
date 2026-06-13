package top.egon.mario.rbac.service.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import top.egon.mario.rbac.service.model.ApiPermissionRule;

import java.util.List;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class DynamicAuthorizationManagerTests {

    private final RbacApiRuleCache apiRuleCache = mock(RbacApiRuleCache.class);
    private final DynamicAuthorizationManager authorizationManager = new DynamicAuthorizationManager(apiRuleCache);

    @Test
    void checkDeniesWhenApiRuleIsMissing() {
        AuthorizationContext context = context(HttpMethod.GET, "/api/admin/users");
        given(apiRuleCache.match("GET", "/api/admin/users")).willReturn(Optional.empty());

        StepVerifier.create(authorizationManager.check(Mono.empty(), context))
                .expectNextMatches(decision -> !decision.isGranted())
                .verifyComplete();
    }

    @Test
    void checkAllowsPublicRuleWithoutAuthentication() {
        AuthorizationContext context = context(HttpMethod.GET, "/actuator/health");
        given(apiRuleCache.match("GET", "/actuator/health"))
                .willReturn(Optional.of(new ApiPermissionRule("api:health", "GET", "/actuator/health", "EXACT", true)));

        StepVerifier.create(authorizationManager.check(Mono.empty(), context))
                .expectNextMatches(AuthorizationDecision::isGranted)
                .verifyComplete();
    }

    @Test
    void checkRequiresMatchingApiAuthority() {
        AuthorizationContext context = context(HttpMethod.GET, "/api/admin/users");
        given(apiRuleCache.match("GET", "/api/admin/users"))
                .willReturn(Optional.of(new ApiPermissionRule("api:rbac:user:list", "GET", "/api/admin/users", "EXACT", false)));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "mario",
                "token",
                List.of(new SimpleGrantedAuthority("api:rbac:user:list"))
        );

        StepVerifier.create(authorizationManager.check(Mono.just(authentication), context))
                .expectNextMatches(AuthorizationDecision::isGranted)
                .verifyComplete();
    }

    private AuthorizationContext context(HttpMethod method, String path) {
        return new AuthorizationContext(MockServerWebExchange.from(MockServerHttpRequest.method(method, path)));
    }

}
