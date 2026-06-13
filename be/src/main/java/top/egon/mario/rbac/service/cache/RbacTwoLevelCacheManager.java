package top.egon.mario.rbac.service.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.rbac.dto.response.EffectivePermissionResponse;
import top.egon.mario.rbac.dto.response.MenuTreeResponse;
import top.egon.mario.rbac.service.model.ApiPermissionRule;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides RBAC permission caches backed by Guava locally and Redis remotely.
 */
@Component
@Slf4j
public class RbacTwoLevelCacheManager implements CacheManager {

    public static final String API_RULES_CACHE = "rbac:permission:api-rules";
    public static final String USER_EFFECTIVE_CACHE = "rbac:permission:user-effective";
    public static final String USER_MENUS_CACHE = "rbac:permission:user-menus";

    private static final String USER_EFFECTIVE_PREFIX = "rbac:permission:user:";
    private static final String EFFECTIVE_SUFFIX = ":effective";
    private static final String MENUS_SUFFIX = ":menus";

    private final Map<String, RbacTwoLevelCache> caches;

    public RbacTwoLevelCacheManager(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            RbacBloomGuards bloomGuards,
            RbacRedisCacheInvalidator invalidator,
            RbacCacheProperties cacheProperties
    ) {
        Map<String, RbacTwoLevelCache> cacheMap = new LinkedHashMap<>();
        cacheMap.put(API_RULES_CACHE, new RbacTwoLevelCache(API_RULES_CACHE,
                key -> RbacPermissionRedisCache.API_RULES_KEY,
                redisTemplate,
                objectMapper,
                bloomGuards,
                invalidator,
                cacheProperties,
                objectMapper.getTypeFactory().constructCollectionType(List.class, ApiPermissionRule.class),
                cacheProperties.apiRulesTtl()));
        cacheMap.put(USER_EFFECTIVE_CACHE, new RbacTwoLevelCache(USER_EFFECTIVE_CACHE,
                key -> USER_EFFECTIVE_PREFIX + key + EFFECTIVE_SUFFIX,
                redisTemplate,
                objectMapper,
                bloomGuards,
                invalidator,
                cacheProperties,
                objectMapper.getTypeFactory().constructType(EffectivePermissionResponse.class),
                cacheProperties.userPermissionsTtl()));
        cacheMap.put(USER_MENUS_CACHE, new RbacTwoLevelCache(USER_MENUS_CACHE,
                key -> USER_EFFECTIVE_PREFIX + key + MENUS_SUFFIX,
                redisTemplate,
                objectMapper,
                bloomGuards,
                invalidator,
                cacheProperties,
                objectMapper.getTypeFactory().constructCollectionType(List.class, MenuTreeResponse.class),
                cacheProperties.userPermissionsTtl()));
        this.caches = Map.copyOf(cacheMap);
    }

    @Override
    public Cache getCache(String name) {
        return caches.get(name);
    }

    @Override
    public Collection<String> getCacheNames() {
        return caches.keySet();
    }

    public RbacTwoLevelCache getRequiredCache(String name) {
        RbacTwoLevelCache cache = caches.get(name);
        if (cache == null) {
            throw new IllegalArgumentException("RBAC cache does not exist: " + name);
        }
        return cache;
    }

    public void clearLocalAllPermissions() {
        caches.values().forEach(RbacTwoLevelCache::clearLocal);
        LogUtil.debug(log).log("rbac two-level local caches cleared, scope=all");
    }

    public void clearLocalUserPermissions(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        RbacTwoLevelCache effectiveCache = getRequiredCache(USER_EFFECTIVE_CACHE);
        RbacTwoLevelCache menuCache = getRequiredCache(USER_MENUS_CACHE);
        userIds.forEach(userId -> {
            effectiveCache.evictLocal(userId);
            menuCache.evictLocal(userId);
        });
        LogUtil.debug(log).log("rbac two-level local caches cleared, scope=users, userCount={}", userIds.size());
    }

}
