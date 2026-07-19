package top.egon.mario.im.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.Striped;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import top.egon.mario.common.utils.LogUtil;

import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

/**
 * Cache-aside join-key lookup with Guava as L1 and Redis as L2.
 *
 * <p>The cached mapping needs no update invalidation because join keys are immutable. Callers still
 * load and lock the resolved database row before changing membership, so status and soft deletion
 * are always checked against the database.</p>
 */
@Component
@Slf4j
public class ImSurfaceJoinKeyCache {

    private static final String REDIS_KEY_PREFIX = "im:surface:join-key:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ImSurfaceJoinKeyCacheProperties properties;
    private final com.google.common.cache.Cache<String, ImSurfaceJoinKeyRef> localCache;
    private final Striped<Lock> keyLocks = Striped.lazyWeakLock(64);

    public ImSurfaceJoinKeyCache(StringRedisTemplate redisTemplate,
                                 ObjectMapper objectMapper,
                                 ImSurfaceJoinKeyCacheProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.localCache = CacheBuilder.newBuilder()
                .maximumSize(properties.localMaximumSize())
                .expireAfterWrite(properties.localTtl())
                .recordStats()
                .build();
    }

    public Optional<ImSurfaceJoinKeyRef> get(
            String joinKey,
            Supplier<Optional<ImSurfaceJoinKeyRef>> loader
    ) {
        if (!properties.enabled()) {
            return loader.get();
        }
        ImSurfaceJoinKeyRef localValue = localCache.getIfPresent(joinKey);
        if (localValue != null) {
            LogUtil.debug(log).log("im surface join-key cache local hit, joinKey={}", joinKey);
            return Optional.of(localValue);
        }
        Optional<ImSurfaceJoinKeyRef> redisValue = readRedis(joinKey);
        if (redisValue.isPresent()) {
            localCache.put(joinKey, redisValue.get());
            LogUtil.debug(log).log("im surface join-key cache redis hit, joinKey={}", joinKey);
            return redisValue;
        }

        Lock lock = keyLocks.get(joinKey);
        lock.lock();
        try {
            localValue = localCache.getIfPresent(joinKey);
            if (localValue != null) {
                return Optional.of(localValue);
            }
            redisValue = readRedis(joinKey);
            if (redisValue.isPresent()) {
                localCache.put(joinKey, redisValue.get());
                return redisValue;
            }
            Optional<ImSurfaceJoinKeyRef> loaded = loader.get();
            loaded.ifPresent(value -> put(joinKey, value));
            return loaded;
        } finally {
            lock.unlock();
        }
    }

    private Optional<ImSurfaceJoinKeyRef> readRedis(String joinKey) {
        try {
            String value = redisTemplate.opsForValue().get(redisKey(joinKey));
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, ImSurfaceJoinKeyRef.class));
        } catch (JsonProcessingException ex) {
            deleteRedis(joinKey);
            LogUtil.warn(log).log("im surface join-key cache value is invalid, joinKey={}", joinKey, ex);
            return Optional.empty();
        } catch (RuntimeException ex) {
            LogUtil.warn(log).log("im surface join-key Redis read failed, joinKey={}", joinKey, ex);
            return Optional.empty();
        }
    }

    private void put(String joinKey, ImSurfaceJoinKeyRef value) {
        localCache.put(joinKey, value);
        try {
            redisTemplate.opsForValue().set(
                    redisKey(joinKey),
                    objectMapper.writeValueAsString(value),
                    properties.redisTtl());
        } catch (JsonProcessingException ex) {
            LogUtil.warn(log).log("im surface join-key cache serialization failed, joinKey={}", joinKey, ex);
        } catch (RuntimeException ex) {
            LogUtil.warn(log).log("im surface join-key Redis write failed, joinKey={}", joinKey, ex);
        }
    }

    private void deleteRedis(String joinKey) {
        try {
            redisTemplate.delete(redisKey(joinKey));
        } catch (RuntimeException ex) {
            LogUtil.warn(log).log("im surface join-key Redis delete failed, joinKey={}", joinKey, ex);
        }
    }

    private String redisKey(String joinKey) {
        return REDIS_KEY_PREFIX + joinKey;
    }
}
