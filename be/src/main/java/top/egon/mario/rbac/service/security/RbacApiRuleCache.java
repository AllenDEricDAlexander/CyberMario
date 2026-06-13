package top.egon.mario.rbac.service.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import top.egon.mario.rbac.service.ApiRuleMatcher;
import top.egon.mario.rbac.service.RbacPermissionService;
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
public class RbacApiRuleCache {

    private final RbacPermissionService permissionService;
    private final ApiRuleMatcher apiRuleMatcher;
    private final AtomicReference<List<ApiPermissionRule>> cachedRules = new AtomicReference<>();

    public Optional<ApiPermissionRule> match(String method, String path) {
        return apiRuleMatcher.match(method, path, rules());
    }

    public void refresh() {
        cachedRules.set(permissionService.findEnabledApiRules());
    }

    @EventListener
    public void onPermissionChanged(RbacPermissionChangedEvent event) {
        refresh();
    }

    private List<ApiPermissionRule> rules() {
        List<ApiPermissionRule> rules = cachedRules.get();
        if (rules == null) {
            rules = permissionService.findEnabledApiRules();
            cachedRules.compareAndSet(null, rules);
        }
        return rules;
    }

}
