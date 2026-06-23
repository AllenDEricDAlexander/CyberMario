package top.egon.mario.rbac.service.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import top.egon.mario.common.utils.LogUtil;

import java.io.IOException;

/**
 * Handles Redis Pub/Sub messages by clearing only local RBAC cache entries.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RbacCacheEvictionSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final RbacTwoLevelCacheManager cacheManager;
    private final RbacCacheInstanceIdentity instanceIdentity;

    @Override
    public void onMessage(@NonNull Message message, byte[] pattern) {
        try {
            RbacCacheEvictionMessage evictionMessage = objectMapper.readValue(message.getBody(), RbacCacheEvictionMessage.class);
            if (instanceIdentity.isLocalSource(evictionMessage.sourceInstanceId())) {
                LogUtil.debug(log).log("rbac cache eviction broadcast skipped, reason=local_source, scope={}",
                        evictionMessage.scope());
                return;
            }
            if (evictionMessage.scope() == RbacCacheEvictionMessage.Scope.PERMISSION_ALL) {
                cacheManager.clearLocalAllPermissions();
            } else if (evictionMessage.scope() == RbacCacheEvictionMessage.Scope.PERMISSION_USERS) {
                cacheManager.clearLocalUserPermissions(evictionMessage.userIds());
            }
            LogUtil.info(log).log("rbac cache eviction broadcast received, scope={}, reason={}",
                    evictionMessage.scope(), evictionMessage.reason());
        } catch (IOException | RuntimeException e) {
            LogUtil.warn(log).log("handle rbac cache eviction broadcast failed", e);
        }
    }

}
