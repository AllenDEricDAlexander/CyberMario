package top.egon.mario.rbac.service.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.Striped;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.rbac.dto.response.EffectivePermissionResponse;
import top.egon.mario.rbac.dto.response.MenuTreeResponse;
import top.egon.mario.rbac.service.model.ApiPermissionRule;
import top.egon.mario.rbac.service.model.RbacPermissionChangedEvent;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
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
    private static final TypeReference<List<ApiPermissionRule>> API_RULE_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<MenuTreeResponse>> MENU_TREE_LIST_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RbacBloomGuards bloomGuards;
    private final RbacCacheProperties cacheProperties;
    private final RbacRedisCacheInvalidator invalidator;
    private final AtomicReference<List<ApiPermissionRule>> localApiRules = new AtomicReference<>();
    private final Striped<Lock> keyLocks = Striped.lazyWeakLock(64);

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
        List<ApiPermissionRule> cached = localApiRules.get();
        if (cached != null) {
            LogUtil.debug(log).log("rbac permission cache local hit, key={}", API_RULES_KEY);
            return cached;
        }
        List<ApiPermissionRule> loaded = getOrLoad(API_RULES_KEY, () -> read(API_RULES_KEY, API_RULE_LIST_TYPE),
                loader, cacheProperties.apiRulesTtl());
        localApiRules.set(loaded);
        return loaded;
    }

    public EffectivePermissionResponse getUserEffectivePermissions(Long userId, Supplier<EffectivePermissionResponse> loader) {
        if (!cacheProperties.enabled()) {
            return loader.get();
        }
        String key = userEffectiveKey(userId);
        return getOrLoad(key, () -> read(key, EffectivePermissionResponse.class), loader, cacheProperties.userPermissionsTtl());
    }

    public List<MenuTreeResponse> getUserMenuTree(Long userId, Supplier<List<MenuTreeResponse>> loader) {
        if (!cacheProperties.enabled()) {
            return loader.get();
        }
        String key = userMenuKey(userId);
        return getOrLoad(key, () -> read(key, MENU_TREE_LIST_TYPE), loader, cacheProperties.userPermissionsTtl());
    }

    public void evictAllPermissions() {
        localApiRules.set(null);
        invalidator.doubleDeletePatterns(List.of(PERMISSION_SCAN_PATTERN));
        LogUtil.info(log).log("rbac permission cache invalidated, scope=all");
    }

    public void evictUserPermissions(Long userId) {
        invalidator.doubleDeleteKeys(List.of(userEffectiveKey(userId), userMenuKey(userId)));
        LogUtil.info(log).log("rbac permission cache invalidated, scope=user, userId={}", userId);
    }

    public void evictUserPermissions(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        invalidator.doubleDeleteKeys(userIds.stream()
                .flatMap(userId -> List.of(userEffectiveKey(userId), userMenuKey(userId)).stream())
                .toList());
        LogUtil.info(log).log("rbac permission cache invalidated, scope=users, userCount={}", userIds.size());
    }

    @EventListener
    public void onPermissionChanged(RbacPermissionChangedEvent event) {
        LogUtil.info(log).log("rbac permission change event received, reason={}", event.reason());
        evictAllPermissions();
    }

    private String userEffectiveKey(Long userId) {
        return USER_EFFECTIVE_PREFIX + userId + EFFECTIVE_SUFFIX;
    }

    private String userMenuKey(Long userId) {
        return USER_EFFECTIVE_PREFIX + userId + MENUS_SUFFIX;
    }

    private <T> Optional<T> read(String key, Class<T> type) {
        if (!bloomGuards.mightContainPermissionKey(key)) {
            return Optional.empty();
        }
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, type));
        } catch (JsonProcessingException e) {
            invalidator.doubleDeleteKeys(List.of(key));
            LogUtil.warn(log).log("rbac permission cache corrupted, key={}", key, e);
            return Optional.empty();
        }
    }

    private <T> Optional<T> read(String key, TypeReference<T> type) {
        if (!bloomGuards.mightContainPermissionKey(key)) {
            return Optional.empty();
        }
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, type));
        } catch (JsonProcessingException e) {
            invalidator.doubleDeleteKeys(List.of(key));
            LogUtil.warn(log).log("rbac permission cache corrupted, key={}", key, e);
            return Optional.empty();
        }
    }

    private <T> T getOrLoad(String key, Supplier<Optional<T>> reader, Supplier<T> loader, Duration ttl) {
        Optional<T> cached = reader.get();
        if (cached.isPresent()) {
            LogUtil.debug(log).log("rbac permission cache hit, key={}", key);
            return cached.get();
        }
        Lock lock = keyLocks.get(key);
        lock.lock();
        try {
            cached = reader.get();
            if (cached.isPresent()) {
                LogUtil.debug(log).log("rbac permission cache hit after lock, key={}", key);
                return cached.get();
            }
            LogUtil.debug(log).log("rbac permission cache miss, key={}", key);
            T loaded = loader.get();
            write(key, loaded, ttl);
            return loaded;
        } finally {
            lock.unlock();
        }
    }

    private void write(String key, Object value, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }
        try {
            bloomGuards.rememberPermissionKey(key);
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttlWithJitter(ttl));
        } catch (JsonProcessingException e) {
            LogUtil.error(log).log("serialize rbac permission cache failed, key={}", key, e);
            throw new IllegalStateException("serialize RBAC permission cache failed", e);
        }
    }

    private Duration ttlWithJitter(Duration ttl) {
        long baseMillis = ttl.toMillis();
        long jitterMillis = Math.max(1, baseMillis / 10);
        return Duration.ofMillis(baseMillis + ThreadLocalRandom.current().nextLong(jitterMillis));
    }

}
