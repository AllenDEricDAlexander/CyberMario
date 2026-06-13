package top.egon.mario.rbac.service.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import top.egon.mario.common.utils.LogUtil;

import java.util.Collection;
import java.util.UUID;

/**
 * Publishes RBAC local cache invalidation messages through Redis Pub/Sub.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RbacCacheEvictionBroadcaster {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RbacCacheProperties cacheProperties;
    private final String sourceInstanceId = UUID.randomUUID().toString();

    public void publishAllPermissions(String reason) {
        publish(RbacCacheEvictionMessage.allPermissions(sourceInstanceId, reason));
    }

    public void publishUserPermissions(Collection<Long> userIds, String reason) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        publish(RbacCacheEvictionMessage.userPermissions(sourceInstanceId, userIds, reason));
    }

    private void publish(RbacCacheEvictionMessage message) {
        if (!cacheProperties.enabled() || !cacheProperties.broadcastEnabled()) {
            return;
        }
        try {
            redisTemplate.convertAndSend(cacheProperties.broadcastTopic(), objectMapper.writeValueAsString(message));
            LogUtil.debug(log).log("rbac cache eviction broadcast published, scope={}, reason={}",
                    message.scope(), message.reason());
        } catch (JsonProcessingException e) {
            LogUtil.warn(log).log("serialize rbac cache eviction broadcast failed, scope={}", message.scope(), e);
        } catch (RuntimeException e) {
            LogUtil.warn(log).log("publish rbac cache eviction broadcast failed, scope={}", message.scope(), e);
        }
    }

}
