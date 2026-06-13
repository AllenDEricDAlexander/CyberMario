package top.egon.mario.rbac.service.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.rbac.service.ApiRuleMatcher;
import top.egon.mario.rbac.service.RbacPermissionService;
import top.egon.mario.rbac.service.cache.RbacPermissionRedisCache;
import top.egon.mario.rbac.service.model.ApiPermissionRule;

import java.util.List;
import java.util.Optional;

/**
 * In-memory cache for enabled API rules used by request authorization.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RbacApiRuleCache {

    private final RbacPermissionService permissionService;
    private final ApiRuleMatcher apiRuleMatcher;
    private final RbacPermissionRedisCache permissionRedisCache;

    public Optional<ApiPermissionRule> match(String method, String path) {
        return apiRuleMatcher.match(method, path, rules());
    }

    public void refresh() {
        List<ApiPermissionRule> rules = permissionRedisCache.getApiRules(permissionService::findEnabledApiRules);
        LogUtil.info(log).log("rbac api rule cache refreshed, ruleCount={}", rules.size());
    }

    private List<ApiPermissionRule> rules() {
        List<ApiPermissionRule> rules = permissionRedisCache.getApiRules(permissionService::findEnabledApiRules);
        LogUtil.debug(log).log("rbac api rule cache loaded, ruleCount={}", rules.size());
        return rules;
    }

}
