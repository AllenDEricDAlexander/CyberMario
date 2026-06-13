package top.egon.mario.rbac.service.cache;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Verifies delayed double-delete cache invalidation.
 */
class RbacRedisCacheInvalidatorTests {

    @Test
    void deletesKeysImmediatelyAndAgainAfterDelay() throws InterruptedException {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RbacRedisCacheInvalidator invalidator = new RbacRedisCacheInvalidator(redisTemplate,
                cacheProperties());

        invalidator.doubleDeleteKeys(List.of("cache:key"));
        Thread.sleep(80);
        invalidator.shutdown();

        verify(redisTemplate, atLeast(2)).delete(List.of("cache:key"));
    }

    @Test
    void shutdownWaitsForScheduledDoubleDeleteBeforeClosingExecutor() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RbacRedisCacheInvalidator invalidator = new RbacRedisCacheInvalidator(redisTemplate,
                cacheProperties());

        invalidator.doubleDeleteKeys(List.of("cache:key"));
        invalidator.shutdown();

        verify(redisTemplate, times(2)).delete(List.of("cache:key"));
    }

    @Test
    void runsInvalidationCallbackAfterImmediateAndDelayedDeletes() throws InterruptedException {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RbacRedisCacheInvalidator invalidator = new RbacRedisCacheInvalidator(redisTemplate,
                cacheProperties());
        AtomicInteger callbackCount = new AtomicInteger();

        invalidator.doubleDeleteKeys(List.of("cache:key"), callbackCount::incrementAndGet);
        Thread.sleep(80);
        invalidator.shutdown();

        assertThat(callbackCount).hasValue(2);
    }

    private RbacCacheProperties cacheProperties() {
        return new RbacCacheProperties(true, Duration.ofMinutes(10), Duration.ofMinutes(10),
                Duration.ofMillis(10), Duration.ofSeconds(30), 1000, 100, 0.01, true, "rbac:cache:evict");
    }

}
