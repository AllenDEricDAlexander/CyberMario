package top.egon.mario.rbac.service.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Verifies Redis Pub/Sub messages for RBAC local cache invalidation.
 */
class RbacCacheEvictionBroadcasterTests {

    @Test
    void publishesPermissionEvictionMessageWhenBroadcastIsEnabled() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        RbacCacheInstanceIdentity instanceIdentity = new RbacCacheInstanceIdentity();
        RbacCacheEvictionBroadcaster broadcaster = new RbacCacheEvictionBroadcaster(redisTemplate,
                objectMapper, cacheProperties(true), instanceIdentity);
        var messageCaptor = forClass(String.class);

        broadcaster.publishUserPermissions(List.of(7L, 8L), "unit-test");

        verify(redisTemplate).convertAndSend(eq("rbac:cache:evict"), messageCaptor.capture());
        RbacCacheEvictionMessage message = objectMapper.readValue(messageCaptor.getValue(), RbacCacheEvictionMessage.class);
        assertThat(message.scope()).isEqualTo(RbacCacheEvictionMessage.Scope.PERMISSION_USERS);
        assertThat(message.userIds()).containsExactly(7L, 8L);
        assertThat(message.reason()).isEqualTo("unit-test");
        assertThat(message.sourceInstanceId()).isEqualTo(instanceIdentity.sourceInstanceId());
    }

    @Test
    void skipsPublishingWhenBroadcastIsDisabled() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RbacCacheEvictionBroadcaster broadcaster = new RbacCacheEvictionBroadcaster(redisTemplate,
                new ObjectMapper(), cacheProperties(false), new RbacCacheInstanceIdentity());

        broadcaster.publishAllPermissions("unit-test");

        verify(redisTemplate, never()).convertAndSend(eq("rbac:cache:evict"), anyString());
    }

    private RbacCacheProperties cacheProperties(boolean broadcastEnabled) {
        return new RbacCacheProperties(true, Duration.ofMinutes(10), Duration.ofMinutes(10),
                Duration.ofMillis(10), Duration.ofSeconds(30), 1000, 100, 0.01, broadcastEnabled, "rbac:cache:evict");
    }

}
