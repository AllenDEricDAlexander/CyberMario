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
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import top.egon.mario.clocktower.resource.ClocktowerRbacResourceProvider;
import top.egon.mario.rbac.service.ApiRuleMatcher;
import top.egon.mario.rbac.service.model.ApiPermissionRule;
import top.egon.mario.rbac.service.resource.RbacResourceProvider;

import java.util.List;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;

class DynamicAuthorizationManagerTests {

    private final RbacApiRuleCache apiRuleCache = mock(RbacApiRuleCache.class);
    private final ApiRuleMatcher apiRuleMatcher = new ApiRuleMatcher();
    private final DynamicAuthorizationManager authorizationManager = new DynamicAuthorizationManager(apiRuleCache, Schedulers.immediate());

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

    @Test
    void checkAllowsClocktowerPlayerForGameViewReplayAndHistoryRules() {
        useRules(providerRules(new ClocktowerRbacResourceProvider()));
        UsernamePasswordAuthenticationToken authentication = authentication(
                "api:clocktower:game:read",
                "api:clocktower:game:replay"
        );

        assertGranted(authentication, HttpMethod.GET, "/api/clocktower/games/9/view");
        assertGranted(authentication, HttpMethod.GET, "/api/clocktower/games/9/replay");
        assertGranted(authentication, HttpMethod.GET, "/api/clocktower/games/history");
    }

    @Test
    void checkDeniesClocktowerPlayerForAdminClocktowerAuditRule() {
        useRules(providerRules(new ClocktowerRbacResourceProvider.AdminProvider()));
        UsernamePasswordAuthenticationToken authentication = authentication(
                "api:clocktower:script:read",
                "api:clocktower:room:read",
                "api:clocktower:game:read",
                "api:clocktower:game:replay",
                "api:clocktower:chat:read"
        );

        StepVerifier.create(authorizationManager.check(Mono.just(authentication),
                        context(HttpMethod.GET, "/api/admin/clocktower/games/9/audit")))
                .expectNextMatches(decision -> !decision.isGranted())
                .verifyComplete();
    }

    @Test
    void checkAllowsClocktowerAdminForAdminClocktowerAuditRule() {
        useRules(providerRules(new ClocktowerRbacResourceProvider.AdminProvider()));
        UsernamePasswordAuthenticationToken authentication = authentication("api:admin:clocktower:audit");

        assertGranted(authentication, HttpMethod.GET, "/api/admin/clocktower/games/9/audit");
    }

    private AuthorizationContext context(HttpMethod method, String path) {
        return new AuthorizationContext(MockServerWebExchange.from(MockServerHttpRequest.method(method, path)));
    }

    private void useRules(List<ApiPermissionRule> rules) {
        given(apiRuleCache.match(anyString(), anyString()))
                .willAnswer(invocation -> apiRuleMatcher.match(invocation.getArgument(0), invocation.getArgument(1), rules));
    }

    private List<ApiPermissionRule> providerRules(RbacResourceProvider provider) {
        return provider.resources().stream()
                .filter(resource -> resource.api() != null)
                .map(resource -> new ApiPermissionRule(resource.code(), resource.api().httpMethod(),
                        resource.api().urlPattern(), resource.api().matcherType(), resource.api().publicFlag()))
                .toList();
    }

    private UsernamePasswordAuthenticationToken authentication(String... permissions) {
        return new UsernamePasswordAuthenticationToken(
                "mario",
                "token",
                List.of(permissions).stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList()
        );
    }

    private void assertGranted(UsernamePasswordAuthenticationToken authentication, HttpMethod method, String path) {
        StepVerifier.create(authorizationManager.check(Mono.just(authentication), context(method, path)))
                .expectNextMatches(AuthorizationDecision::isGranted)
                .verifyComplete();
    }

}
