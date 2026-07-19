package top.egon.mario.im;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import top.egon.mario.im.cache.ImSurfaceJoinKeyCache;
import top.egon.mario.im.cache.ImSurfaceJoinKeyCacheProperties;
import top.egon.mario.im.cache.ImSurfaceJoinKeyRef;
import top.egon.mario.im.po.enums.ImSurfaceType;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImSurfaceJoinKeyCacheTests {

    private static final String JOIN_KEY = "chn_0123456789abcdefghijkl";
    private static final String REDIS_KEY = "im:surface:join-key:" + JOIN_KEY;
    private static final Duration REDIS_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void redisHitPopulatesLocalCacheAndAvoidsDatabaseLoader() throws Exception {
        ImSurfaceJoinKeyRef cached = new ImSurfaceJoinKeyRef(ImSurfaceType.CHANNEL, 7L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY)).thenReturn(objectMapper.writeValueAsString(cached));
        ImSurfaceJoinKeyCache cache = cache(true);
        AtomicInteger loads = new AtomicInteger();

        assertThat(cache.get(JOIN_KEY, () -> {
            loads.incrementAndGet();
            return Optional.empty();
        })).contains(cached);
        assertThat(cache.get(JOIN_KEY, () -> {
            loads.incrementAndGet();
            return Optional.empty();
        })).contains(cached);

        assertThat(loads).hasValue(0);
        verify(valueOperations).get(REDIS_KEY);
    }

    @Test
    void cacheMissLoadsDatabaseAndWritesBothCacheLevels() throws Exception {
        ImSurfaceJoinKeyRef loaded = new ImSurfaceJoinKeyRef(ImSurfaceType.CHANNEL, 8L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ImSurfaceJoinKeyCache cache = cache(true);
        AtomicInteger loads = new AtomicInteger();

        assertThat(cache.get(JOIN_KEY, () -> {
            loads.incrementAndGet();
            return Optional.of(loaded);
        })).contains(loaded);
        assertThat(cache.get(JOIN_KEY, Optional::empty)).contains(loaded);

        assertThat(loads).hasValue(1);
        verify(valueOperations).set(REDIS_KEY, objectMapper.writeValueAsString(loaded), REDIS_TTL);
    }

    @Test
    void disabledCacheDelegatesDirectlyToDatabaseLoader() {
        ImSurfaceJoinKeyRef loaded = new ImSurfaceJoinKeyRef(ImSurfaceType.CHANNEL, 9L);
        ImSurfaceJoinKeyCache cache = cache(false);

        assertThat(cache.get(JOIN_KEY, () -> Optional.of(loaded))).contains(loaded);

        verify(redisTemplate, never()).opsForValue();
    }

    private ImSurfaceJoinKeyCache cache(boolean enabled) {
        return new ImSurfaceJoinKeyCache(
                redisTemplate,
                objectMapper,
                new ImSurfaceJoinKeyCacheProperties(
                        enabled, REDIS_TTL, Duration.ofMinutes(10), 100));
    }
}
