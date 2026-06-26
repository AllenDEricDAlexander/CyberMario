package top.egon.mario.agent.mcp.runtime;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Coordinates local MCP runtime refresh and cross-node Redis broadcast.
 */
@Component
@ConditionalOnProperty(prefix = "agent.mcp.runtime", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class McpRuntimeRefreshCoordinator {

    private final DynamicMcpClientManager clientManager;
    private final McpRuntimeRefreshBroadcaster broadcaster;

    public void refreshServer(Long serverId, String reason) {
        clientManager.refreshServer(serverId);
        broadcaster.publishServerRefresh(serverId, reason);
    }

    public void disableServer(Long serverId, String reason) {
        clientManager.disableServer(serverId);
        broadcaster.publishServerDisable(serverId, reason);
    }

    public void refreshAll(String reason) {
        clientManager.reloadEnabledServers();
        broadcaster.publishAllRefresh(reason);
    }

    public void applyRemote(McpRuntimeRefreshMessage message) {
        switch (message.eventType()) {
            case SERVER_REFRESH -> clientManager.refreshServer(message.serverId());
            case SERVER_DISABLE -> clientManager.refreshServer(message.serverId());
            case ALL_REFRESH -> clientManager.reloadEnabledServers();
        }
    }

}
