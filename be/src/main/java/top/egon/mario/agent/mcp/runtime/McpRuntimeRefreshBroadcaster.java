package top.egon.mario.agent.mcp.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import top.egon.mario.common.utils.LogUtil;

/**
 * Publishes MCP runtime refresh messages through Redis Pub/Sub.
 */
@Component
@ConditionalOnProperty(prefix = "agent.mcp.runtime", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class McpRuntimeRefreshBroadcaster {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final McpRuntimeRefreshProperties refreshProperties;
    private final McpRuntimeInstanceIdentity instanceIdentity;

    public void publishServerRefresh(Long serverId, String reason) {
        publish(McpRuntimeRefreshMessage.serverRefresh(instanceIdentity.sourceInstanceId(), serverId, reason));
    }

    public void publishServerDisable(Long serverId, String reason) {
        publish(McpRuntimeRefreshMessage.serverDisable(instanceIdentity.sourceInstanceId(), serverId, reason));
    }

    public void publishAllRefresh(String reason) {
        publish(McpRuntimeRefreshMessage.allRefresh(instanceIdentity.sourceInstanceId(), reason));
    }

    private void publish(McpRuntimeRefreshMessage message) {
        if (!Boolean.TRUE.equals(refreshProperties.broadcastEnabled())) {
            return;
        }
        try {
            redisTemplate.convertAndSend(refreshProperties.broadcastTopic(), objectMapper.writeValueAsString(message));
            LogUtil.debug(log).log("mcp runtime refresh broadcast published, eventType={}, serverId={}, reason={}",
                    message.eventType(), message.serverId(), message.reason());
        } catch (JsonProcessingException e) {
            LogUtil.warn(log).log("serialize mcp runtime refresh broadcast failed, eventType={}, serverId={}",
                    message.eventType(), message.serverId(), e);
        } catch (RuntimeException e) {
            LogUtil.warn(log).log("publish mcp runtime refresh broadcast failed, eventType={}, serverId={}",
                    message.eventType(), message.serverId(), e);
        }
    }

}
