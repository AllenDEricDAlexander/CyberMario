package top.egon.mario.rbac.service.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import top.egon.mario.rbac.dto.response.EffectivePermissionResponse;
import top.egon.mario.rbac.po.enums.ApiMatcherType;
import top.egon.mario.rbac.service.model.ApiPermissionRule;
import top.egon.mario.rbac.service.model.RbacPermissionChangedEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifies Redis-backed RBAC permission cache behavior.
 */
class RbacPermissionRedisCacheTests {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RbacRedisCacheInvalidator invalidator;
    private RbacCacheEvictionBroadcaster broadcaster;
    private RbacPermissionRedisCache permissionCache;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        invalidator = mock(RbacRedisCacheInvalidator.class);
        broadcaster = mock(RbacCacheEvictionBroadcaster.class);
        ObjectMapper objectMapper = new ObjectMapper();
        RbacCacheProperties cacheProperties = cacheProperties();
        RbacBloomGuards bloomGuards = new RbacBloomGuards(cacheProperties);
        RbacTwoLevelCacheManager cacheManager = new RbacTwoLevelCacheManager(redisTemplate,
                objectMapper, bloomGuards, invalidator, cacheProperties);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        permissionCache = new RbacPermissionRedisCache(redisTemplate, bloomGuards,
                cacheProperties, invalidator, cacheManager, broadcaster);
    }

    @Test
    void loadsApiRulesFromDatabaseAndWritesRedisWhenCacheMisses() {
        AtomicInteger loadCount = new AtomicInteger();
        List<ApiPermissionRule> loadedRules = List.of(new ApiPermissionRule("api:demo", "GET", "/api/demo", ApiMatcherType.EXACT, false));

        List<ApiPermissionRule> rules = permissionCache.getApiRules(() -> {
            loadCount.incrementAndGet();
            return loadedRules;
        });
        List<ApiPermissionRule> secondRead = permissionCache.getApiRules(() -> {
            loadCount.incrementAndGet();
            return List.of();
        });

        assertThat(rules).isEqualTo(loadedRules);
        assertThat(secondRead).isEqualTo(loadedRules);
        assertThat(loadCount).hasValue(1);
        verify(valueOperations).set(eq(RbacPermissionRedisCache.API_RULES_KEY), any(String.class), any(Duration.class));
    }

    @Test
    void onlyLoadsUserPermissionsOnceWhenConcurrentRequestsMissSameKey() throws Exception {
        Map<String, String> redisValues = new ConcurrentHashMap<>();
        given(valueOperations.get(any(String.class))).willAnswer(invocation -> redisValues.get(invocation.getArgument(0)));
        willAnswer(invocation -> {
            redisValues.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).given(valueOperations).set(any(String.class), any(String.class), any(Duration.class));
        EffectivePermissionResponse loadedPermissions = EffectivePermissionResponse.builder()
                .roleIds(Set.of(1L))
                .roleCodes(Set.of("ADMIN"))
                .menuCodes(Set.of("menu:dashboard"))
                .buttonCodes(Set.of("button:save"))
                .apiCodes(Set.of("api:demo"))
                .build();
        AtomicInteger loadCount = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(6);
        List<Callable<EffectivePermissionResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            tasks.add(() -> {
                start.await();
                return permissionCache.getUserEffectivePermissions(7L, () -> {
                    loadCount.incrementAndGet();
                    sleepQuietly();
                    return loadedPermissions;
                });
            });
        }

        List<Future<EffectivePermissionResponse>> futures = tasks.stream()
                .map(executor::submit)
                .toList();
        start.countDown();
        for (Future<EffectivePermissionResponse> future : futures) {
            assertThat(future.get()).isEqualTo(loadedPermissions);
        }
        executor.shutdownNow();

        assertThat(loadCount).hasValue(1);
    }

    @Test
    void evictsPermissionKeysWithDelayedDoubleDelete() {
        permissionCache.evictAllPermissions();

        verify(invalidator).doubleDeletePatterns(eq(List.of("rbac:permission:*")), any(Runnable.class));
    }

    @Test
    void evictsPermissionKeysWhenPermissionChangedEventArrives() {
        permissionCache.onPermissionChanged(new RbacPermissionChangedEvent("unit-test"));

        verify(invalidator).doubleDeletePatterns(eq(List.of("rbac:permission:*")), any(Runnable.class));
    }

    @Test
    void publishesLocalEvictionBroadcastWhenPermissionChangedEventArrives() {
        var callbackCaptor = forClass(Runnable.class);
        permissionCache.onPermissionChanged(new RbacPermissionChangedEvent("unit-test"));
        verify(invalidator).doubleDeletePatterns(eq(List.of("rbac:permission:*")), callbackCaptor.capture());

        callbackCaptor.getValue().run();

        verify(broadcaster).publishAllPermissions("unit-test");
    }

    private RbacCacheProperties cacheProperties() {
        return new RbacCacheProperties(true, Duration.ofMinutes(10), Duration.ofMinutes(10),
                Duration.ofMillis(10), Duration.ofSeconds(30), 1000, 100, 0.01, true, "rbac:cache:evict");
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
