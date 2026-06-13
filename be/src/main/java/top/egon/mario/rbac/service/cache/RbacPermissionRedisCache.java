package top.egon.mario.rbac.service.cache;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.rbac.dto.response.EffectivePermissionResponse;
import top.egon.mario.rbac.dto.response.MenuTreeResponse;
import top.egon.mario.rbac.service.model.ApiPermissionRule;
import top.egon.mario.rbac.service.model.RbacPermissionChangedEvent;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * Redis-backed permission cache with local hot snapshots for authorization.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RbacPermissionRedisCache {

    public static final String API_RULES_KEY = "rbac:permission:api-rules";
    private static final String PERMISSION_SCAN_PATTERN = "rbac:permission:*";
    private static final String USER_EFFECTIVE_PREFIX = "rbac:permission:user:";
    private static final String EFFECTIVE_SUFFIX = ":effective";
    private static final String MENUS_SUFFIX = ":menus";

    private final StringRedisTemplate redisTemplate;
    private final RbacBloomGuards bloomGuards;
    private final RbacCacheProperties cacheProperties;
    private final RbacRedisCacheInvalidator invalidator;
    private final RbacTwoLevelCacheManager cacheManager;
    private final RbacCacheEvictionBroadcaster broadcaster;

    @PostConstruct
    public void warmBloomFromRedis() {
        if (!cacheProperties.enabled()) {
            return;
        }
        bloomGuards.rememberPermissionKey(API_RULES_KEY);
        try (Cursor<String> cursor = redisTemplate.scan(ScanOptions.scanOptions().match(PERMISSION_SCAN_PATTERN).count(1000).build())) {
            while (cursor.hasNext()) {
                bloomGuards.rememberPermissionKey(cursor.next());
            }
            LogUtil.info(log).log("rbac permission cache bloom warmed from redis");
        } catch (RuntimeException e) {
            // Redis may be unavailable during local startup; cache writes will warm the Bloom filter later.
            LogUtil.warn(log).log("rbac permission cache bloom warm skipped, reason=redis_unavailable", e);
        }
    }

    public List<ApiPermissionRule> getApiRules(Supplier<List<ApiPermissionRule>> loader) {
        if (!cacheProperties.enabled()) {
            return loader.get();
        }
        return cacheManager.getRequiredCache(RbacTwoLevelCacheManager.API_RULES_CACHE)
                .get(API_RULES_KEY, loader::get);
    }

    public EffectivePermissionResponse getUserEffectivePermissions(Long userId, Supplier<EffectivePermissionResponse> loader) {
        if (!cacheProperties.enabled()) {
            return loader.get();
        }
        return cacheManager.getRequiredCache(RbacTwoLevelCacheManager.USER_EFFECTIVE_CACHE)
                .get(userId, loader::get);
    }

    public List<MenuTreeResponse> getUserMenuTree(Long userId, Supplier<List<MenuTreeResponse>> loader) {
        if (!cacheProperties.enabled()) {
            return loader.get();
        }
        return cacheManager.getRequiredCache(RbacTwoLevelCacheManager.USER_MENUS_CACHE)
                .get(userId, loader::get);
    }

    public void evictAllPermissions() {
        evictAllPermissions("manual");
    }

    public void evictUserPermissions(Long userId) {
        invalidator.doubleDeleteKeys(List.of(userEffectiveKey(userId), userMenuKey(userId)), () -> {
            cacheManager.clearLocalUserPermissions(List.of(userId));
            broadcaster.publishUserPermissions(List.of(userId), "manual");
        });
        LogUtil.info(log).log("rbac permission cache invalidated, scope=user, userId={}", userId);
    }

    public void evictUserPermissions(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        List<Long> cacheUserIds = List.copyOf(userIds);
        invalidator.doubleDeleteKeys(cacheUserIds.stream()
                .flatMap(userId -> List.of(userEffectiveKey(userId), userMenuKey(userId)).stream())
                .toList(), () -> {
            cacheManager.clearLocalUserPermissions(cacheUserIds);
            broadcaster.publishUserPermissions(cacheUserIds, "manual");
        });
        LogUtil.info(log).log("rbac permission cache invalidated, scope=users, userCount={}", cacheUserIds.size());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPermissionChanged(RbacPermissionChangedEvent event) {
        LogUtil.info(log).log("rbac permission change event received, reason={}", event.reason());
        evictAllPermissions(event.reason());
    }

    private String userEffectiveKey(Long userId) {
        return USER_EFFECTIVE_PREFIX + userId + EFFECTIVE_SUFFIX;
    }

    private String userMenuKey(Long userId) {
        return USER_EFFECTIVE_PREFIX + userId + MENUS_SUFFIX;
    }

    private void evictAllPermissions(String reason) {
        invalidator.doubleDeletePatterns(List.of(PERMISSION_SCAN_PATTERN), () -> {
            cacheManager.clearLocalAllPermissions();
            broadcaster.publishAllPermissions(reason);
        });
        LogUtil.info(log).log("rbac permission cache invalidated, scope=all");
    }

}
