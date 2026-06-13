package top.egon.mario.rbac.service.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifies Redis Pub/Sub local cache eviction handling.
 */
class RbacCacheEvictionSubscriberTests {

    @Test
    void clearsOnlyLocalUserPermissionCachesWhenUserEvictionMessageArrives() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        RbacTwoLevelCacheManager cacheManager = mock(RbacTwoLevelCacheManager.class);
        RbacCacheEvictionSubscriber subscriber = new RbacCacheEvictionSubscriber(objectMapper, cacheManager);
        RbacCacheEvictionMessage evictionMessage = RbacCacheEvictionMessage.userPermissions("node-1",
                List.of(7L, 8L), "unit-test");
        Message redisMessage = mock(Message.class);
        given(redisMessage.getBody()).willReturn(objectMapper.writeValueAsBytes(evictionMessage));

        subscriber.onMessage(redisMessage, "rbac:cache:evict".getBytes(StandardCharsets.UTF_8));

        verify(cacheManager).clearLocalUserPermissions(List.of(7L, 8L));
    }

    @Test
    void clearsOnlyLocalPermissionCachesWhenAllPermissionEvictionMessageArrives() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        RbacTwoLevelCacheManager cacheManager = mock(RbacTwoLevelCacheManager.class);
        RbacCacheEvictionSubscriber subscriber = new RbacCacheEvictionSubscriber(objectMapper, cacheManager);
        RbacCacheEvictionMessage evictionMessage = RbacCacheEvictionMessage.allPermissions("node-1", "unit-test");
        Message redisMessage = mock(Message.class);
        given(redisMessage.getBody()).willReturn(objectMapper.writeValueAsBytes(evictionMessage));

        subscriber.onMessage(redisMessage, "rbac:cache:evict".getBytes(StandardCharsets.UTF_8));

        verify(cacheManager).clearLocalAllPermissions();
    }

    private RbacCacheProperties cacheProperties() {
        return new RbacCacheProperties(true, Duration.ofMinutes(10), Duration.ofMinutes(10),
                Duration.ofMillis(10), Duration.ofSeconds(30), 1000, 100, 0.01, true, "rbac:cache:evict");
    }

}
