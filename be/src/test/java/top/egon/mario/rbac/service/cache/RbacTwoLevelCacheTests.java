package top.egon.mario.rbac.service.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import top.egon.mario.rbac.dto.response.EffectivePermissionResponse;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Verifies Guava and Redis two-level RBAC cache behavior.
 */
class RbacTwoLevelCacheTests {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ObjectMapper objectMapper;
    private RbacBloomGuards bloomGuards;
    private RbacTwoLevelCache cache;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        objectMapper = new ObjectMapper();
        RbacCacheProperties cacheProperties = cacheProperties();
        bloomGuards = new RbacBloomGuards(cacheProperties);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        cache = new RbacTwoLevelCache("rbac:permission:user-effective",
                key -> "rbac:permission:user:" + key + ":effective",
                redisTemplate,
                objectMapper,
                bloomGuards,
                mock(RbacRedisCacheInvalidator.class),
                cacheProperties,
                objectMapper.getTypeFactory().constructType(EffectivePermissionResponse.class),
                cacheProperties.userPermissionsTtl());
    }

    @Test
    void readsRedisAndBackfillsLocalCacheWhenLocalMisses() throws Exception {
        EffectivePermissionResponse cachedPermissions = permissions("ADMIN");
        String redisKey = "rbac:permission:user:7:effective";
        bloomGuards.rememberPermissionKey(redisKey);
        given(valueOperations.get(redisKey)).willReturn(objectMapper.writeValueAsString(cachedPermissions));

        EffectivePermissionResponse firstRead = cache.get(7L, () -> fail("loader should not run when Redis contains value"));
        EffectivePermissionResponse secondRead = cache.get(7L, () -> fail("loader should not run after local backfill"));

        assertThat(firstRead).isEqualTo(cachedPermissions);
        assertThat(secondRead).isEqualTo(cachedPermissions);
        verify(valueOperations, times(1)).get(redisKey);
    }

    @Test
    void loadsOnceAndWritesRedisBeforeLocalCacheWhenBothLevelsMiss() {
        AtomicInteger loadCount = new AtomicInteger();
        EffectivePermissionResponse loadedPermissions = permissions("USER");

        EffectivePermissionResponse firstRead = cache.get(8L, () -> {
            loadCount.incrementAndGet();
            return loadedPermissions;
        });
        EffectivePermissionResponse secondRead = cache.get(8L, () -> {
            loadCount.incrementAndGet();
            return permissions("OTHER");
        });

        assertThat(firstRead).isEqualTo(loadedPermissions);
        assertThat(secondRead).isEqualTo(loadedPermissions);
        assertThat(loadCount).hasValue(1);
        verify(valueOperations).set(eq("rbac:permission:user:8:effective"), any(String.class), any(Duration.class));
    }

    private EffectivePermissionResponse permissions(String roleCode) {
        return EffectivePermissionResponse.builder()
                .roleIds(Set.of(1L))
                .roleCodes(Set.of(roleCode))
                .menuCodes(Set.of("menu:dashboard"))
                .buttonCodes(Set.of("button:save"))
                .apiCodes(Set.of("api:demo"))
                .build();
    }

    private RbacCacheProperties cacheProperties() {
        return new RbacCacheProperties(true, Duration.ofMinutes(10), Duration.ofMinutes(10),
                Duration.ofMillis(10), Duration.ofSeconds(30), 1000, 100, 0.01, true, "rbac:cache:evict");
    }

}
