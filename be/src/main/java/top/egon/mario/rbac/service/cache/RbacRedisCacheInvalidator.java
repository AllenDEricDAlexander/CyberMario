package top.egon.mario.rbac.service.cache;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import top.egon.mario.common.utils.LogUtil;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Applies delayed double-delete invalidation for Redis cache keys.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RbacRedisCacheInvalidator {

    private final StringRedisTemplate redisTemplate;
    private final RbacCacheProperties cacheProperties;
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "rbac-cache-double-delete");
        thread.setDaemon(true);
        return thread;
    });

    public void doubleDeleteKeys(Collection<String> keys) {
        if (!cacheProperties.enabled()) {
            return;
        }
        if (keys == null || keys.isEmpty()) {
            return;
        }
        List<String> cacheKeys = List.copyOf(keys);
        deleteKeys(cacheKeys);
        executorService.schedule(() -> deleteKeys(cacheKeys), delayMillis(), TimeUnit.MILLISECONDS);
        LogUtil.debug(log).log("rbac cache keys scheduled for double delete, keyCount={}", cacheKeys.size());
    }

    public void doubleDeletePatterns(Collection<String> patterns) {
        if (!cacheProperties.enabled()) {
            return;
        }
        if (patterns == null || patterns.isEmpty()) {
            return;
        }
        List<String> cachePatterns = List.copyOf(patterns);
        deletePatterns(cachePatterns);
        executorService.schedule(() -> deletePatterns(cachePatterns), delayMillis(), TimeUnit.MILLISECONDS);
        LogUtil.debug(log).log("rbac cache patterns scheduled for double delete, patternCount={}", cachePatterns.size());
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }

    private void deleteKeys(Collection<String> keys) {
        redisTemplate.delete(keys);
        LogUtil.debug(log).log("rbac cache keys deleted, keyCount={}", keys.size());
    }

    private void deletePatterns(Collection<String> patterns) {
        for (String pattern : patterns) {
            try (Cursor<String> cursor = redisTemplate.scan(ScanOptions.scanOptions().match(pattern).count(1000).build())) {
                while (cursor.hasNext()) {
                    redisTemplate.delete(cursor.next());
                }
            }
        }
    }

    private long delayMillis() {
        Duration delay = cacheProperties.doubleDeleteDelay();
        return delay == null ? 800 : Math.max(delay.toMillis(), 1);
    }

}
