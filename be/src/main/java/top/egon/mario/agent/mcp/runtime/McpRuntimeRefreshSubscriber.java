package top.egon.mario.agent.mcp.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import top.egon.mario.common.utils.LogUtil;

import java.io.IOException;

/**
 * Handles Redis Pub/Sub messages by applying MCP runtime refresh to the local node.
 */
@Component
@ConditionalOnProperty(prefix = "agent.mcp.runtime", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class McpRuntimeRefreshSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final McpRuntimeRefreshCoordinator coordinator;
    private final McpRuntimeInstanceIdentity instanceIdentity;

    @Override
    public void onMessage(@NonNull Message message, byte[] pattern) {
        try {
            McpRuntimeRefreshMessage refreshMessage = objectMapper.readValue(message.getBody(),
                    McpRuntimeRefreshMessage.class);
            if (instanceIdentity.isLocalSource(refreshMessage.sourceInstanceId())) {
                LogUtil.debug(log).log("mcp runtime refresh broadcast skipped, reason=local_source, eventType={}, serverId={}",
                        refreshMessage.eventType(), refreshMessage.serverId());
                return;
            }
            if (!isValid(refreshMessage)) {
                LogUtil.warn(log).log("mcp runtime refresh broadcast skipped, reason=invalid_message, eventType={}, serverId={}",
                        refreshMessage.eventType(), refreshMessage.serverId());
                return;
            }

            coordinator.applyRemote(refreshMessage);
            LogUtil.info(log).log("mcp runtime refresh broadcast received, eventType={}, serverId={}, reason={}",
                    refreshMessage.eventType(), refreshMessage.serverId(), refreshMessage.reason());
        } catch (IOException | RuntimeException e) {
            LogUtil.warn(log).log("handle mcp runtime refresh broadcast failed", e);
        }
    }

    private boolean isValid(McpRuntimeRefreshMessage message) {
        if (message.eventType() == null) {
            return false;
        }
        return message.eventType() == McpRuntimeRefreshMessage.EventType.ALL_REFRESH || message.serverId() != null;
    }

}
