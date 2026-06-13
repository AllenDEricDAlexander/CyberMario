package top.egon.mario.rbac.service.security;

import org.junit.jupiter.api.Test;
import top.egon.mario.rbac.po.enums.ApiMatcherType;
import top.egon.mario.rbac.service.ApiRuleMatcher;
import top.egon.mario.rbac.service.RbacPermissionService;
import top.egon.mario.rbac.service.cache.RbacPermissionRedisCache;
import top.egon.mario.rbac.service.model.ApiPermissionRule;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Verifies API rule lookup delegates local caching to the RBAC permission cache.
 */
class RbacApiRuleCacheTests {

    @Test
    void readsApiRulesFromPermissionCacheForEachMatch() {
        RbacPermissionService permissionService = mock(RbacPermissionService.class);
        ApiRuleMatcher apiRuleMatcher = mock(ApiRuleMatcher.class);
        RbacPermissionRedisCache permissionRedisCache = mock(RbacPermissionRedisCache.class);
        RbacApiRuleCache apiRuleCache = new RbacApiRuleCache(permissionService, apiRuleMatcher, permissionRedisCache);
        ApiPermissionRule rule = new ApiPermissionRule("api:demo", "GET", "/api/demo", ApiMatcherType.EXACT, false);
        given(permissionRedisCache.getApiRules(any())).willReturn(List.of(rule));
        given(apiRuleMatcher.match("GET", "/api/demo", List.of(rule))).willReturn(Optional.of(rule));

        apiRuleCache.match("GET", "/api/demo");
        apiRuleCache.match("GET", "/api/demo");

        verify(permissionRedisCache, times(2)).getApiRules(any());
    }

}
