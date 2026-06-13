package top.egon.mario.rbac.service.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import top.egon.mario.rbac.service.ApiRuleMatcher;
import top.egon.mario.rbac.service.RbacPermissionService;
import top.egon.mario.rbac.service.cache.RbacPermissionRedisCache;
import top.egon.mario.rbac.service.model.ApiPermissionRule;
import top.egon.mario.rbac.service.model.RbacPermissionChangedEvent;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

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
    private final AtomicReference<List<ApiPermissionRule>> cachedRules = new AtomicReference<>();

    public Optional<ApiPermissionRule> match(String method, String path) {
        return apiRuleMatcher.match(method, path, rules());
    }

    public void refresh() {
        List<ApiPermissionRule> rules = permissionRedisCache.getApiRules(permissionService::findEnabledApiRules);
        cachedRules.set(rules);
        log.info("rbac api rule cache refreshed, ruleCount={}", rules.size());
    }

    @EventListener
    public void onPermissionChanged(RbacPermissionChangedEvent event) {
        cachedRules.set(null);
        log.info("rbac api rule local cache cleared, reason={}", event.reason());
    }

    private List<ApiPermissionRule> rules() {
        List<ApiPermissionRule> rules = cachedRules.get();
        if (rules == null) {
            rules = permissionRedisCache.getApiRules(permissionService::findEnabledApiRules);
            cachedRules.compareAndSet(null, rules);
            if (log.isDebugEnabled()) {
                log.debug("rbac api rule local cache loaded, ruleCount={}", rules.size());
            }
        }
        return rules;
    }

}
