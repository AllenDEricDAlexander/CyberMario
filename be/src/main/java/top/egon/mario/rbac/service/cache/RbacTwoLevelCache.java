package top.egon.mario.rbac.service.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.Striped;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import top.egon.mario.common.utils.LogUtil;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;

/**
 * Guava and Redis two-level cache for RBAC permission read models.
 */
@Slf4j
public class RbacTwoLevelCache implements Cache {

    private final String name;
    private final Function<Object, String> redisKeyResolver;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RbacBloomGuards bloomGuards;
    private final RbacRedisCacheInvalidator invalidator;
    private final RbacCacheProperties cacheProperties;
    private final JavaType valueType;
    private final Duration ttl;
    private final com.google.common.cache.Cache<Object, Object> localCache;
    private final Striped<Lock> keyLocks = Striped.lazyWeakLock(64);

    public RbacTwoLevelCache(
            String name,
            Function<Object, String> redisKeyResolver,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            RbacBloomGuards bloomGuards,
            RbacRedisCacheInvalidator invalidator,
            RbacCacheProperties cacheProperties,
            JavaType valueType,
            Duration ttl
    ) {
        this.name = name;
        this.redisKeyResolver = redisKeyResolver;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.bloomGuards = bloomGuards;
        this.invalidator = invalidator;
        this.cacheProperties = cacheProperties;
        this.valueType = valueType;
        this.ttl = ttl;
        this.localCache = CacheBuilder.newBuilder()
                .maximumSize(cacheProperties.localMaximumSize())
                .expireAfterWrite(cacheProperties.localTtl())
                .recordStats()
                .build();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return Map.of("local", localCache, "redis", redisTemplate);
    }

    @Override
    @Nullable
    public ValueWrapper get(Object key) {
        if (!cacheProperties.enabled()) {
            return null;
        }
        Object localValue = localCache.getIfPresent(key);
        if (localValue != null) {
            LogUtil.debug(log).log("rbac two-level cache local hit, cache={}, key={}", name, key);
            return new SimpleValueWrapper(localValue);
        }
        Object redisValue = readRedis(key);
        if (redisValue == null) {
            return null;
        }
        localCache.put(key, redisValue);
        LogUtil.debug(log).log("rbac two-level cache redis hit, cache={}, key={}", name, key);
        return new SimpleValueWrapper(redisValue);
    }

    @Override
    @Nullable
    public <T> T get(Object key, @Nullable Class<T> type) {
        ValueWrapper wrapper = get(key);
        if (wrapper == null) {
            return null;
        }
        Object value = wrapper.get();
        if (type == null) {
            @SuppressWarnings("unchecked")
            T castValue = (T) value;
            return castValue;
        }
        return type.cast(value);
    }

    @Override
    @Nullable
    public <T> T get(Object key, Callable<T> valueLoader) {
        if (!cacheProperties.enabled()) {
            return load(key, valueLoader);
        }
        ValueWrapper cached = get(key);
        if (cached != null) {
            @SuppressWarnings("unchecked")
            T value = (T) cached.get();
            return value;
        }
        Lock lock = keyLocks.get(key);
        lock.lock();
        try {
            cached = get(key);
            if (cached != null) {
                @SuppressWarnings("unchecked")
                T value = (T) cached.get();
                return value;
            }
            LogUtil.debug(log).log("rbac two-level cache miss, cache={}, key={}", name, key);
            T loaded = load(key, valueLoader);
            put(key, loaded);
            return loaded;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void put(Object key, @Nullable Object value) {
        if (!cacheProperties.enabled() || value == null) {
            return;
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }
        String redisKey = redisKeyResolver.apply(key);
        try {
            bloomGuards.rememberPermissionKey(redisKey);
            redisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(value), ttlWithJitter(ttl));
            localCache.put(key, value);
            LogUtil.debug(log).log("rbac two-level cache stored, cache={}, key={}", name, key);
        } catch (JsonProcessingException e) {
            LogUtil.error(log).log("serialize rbac two-level cache failed, cache={}, key={}", name, key, e);
            throw new IllegalStateException("serialize RBAC two-level cache failed", e);
        }
    }

    @Override
    @Nullable
    public ValueWrapper putIfAbsent(Object key, @Nullable Object value) {
        ValueWrapper cached = get(key);
        if (cached != null) {
            return cached;
        }
        Lock lock = keyLocks.get(key);
        lock.lock();
        try {
            cached = get(key);
            if (cached != null) {
                return cached;
            }
            put(key, value);
            return null;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void evict(Object key) {
        redisTemplate.delete(redisKeyResolver.apply(key));
        evictLocal(key);
    }

    @Override
    public boolean evictIfPresent(Object key) {
        boolean localPresent = localCache.getIfPresent(key) != null;
        evict(key);
        return localPresent;
    }

    @Override
    public void clear() {
        clearLocal();
    }

    @Override
    public boolean invalidate() {
        boolean hadLocalEntries = localCache.size() > 0;
        clearLocal();
        return hadLocalEntries;
    }

    public void evictLocal(Object key) {
        localCache.invalidate(key);
    }

    public void clearLocal() {
        localCache.invalidateAll();
    }

    private Object readRedis(Object key) {
        String redisKey = redisKeyResolver.apply(key);
        if (!bloomGuards.mightContainPermissionKey(redisKey)) {
            return null;
        }
        String value = redisTemplate.opsForValue().get(redisKey);
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, valueType);
        } catch (JsonProcessingException e) {
            invalidator.doubleDeleteKeys(List.of(redisKey));
            LogUtil.warn(log).log("rbac two-level cache corrupted, cache={}, key={}", name, key, e);
            return null;
        }
    }

    private <T> T load(Object key, Callable<T> valueLoader) {
        try {
            return valueLoader.call();
        } catch (Exception e) {
            throw new ValueRetrievalException(key, valueLoader, e);
        }
    }

    private Duration ttlWithJitter(Duration ttl) {
        long baseMillis = ttl.toMillis();
        long jitterMillis = Math.max(1, baseMillis / 10);
        return Duration.ofMillis(baseMillis + ThreadLocalRandom.current().nextLong(jitterMillis));
    }

}
