package top.egon.mario.agent.mcp.runtime;

import java.time.Instant;

/**
 * Redis Pub/Sub payload for cross-node MCP runtime refresh.
 */
public record McpRuntimeRefreshMessage(
        String sourceInstanceId,
        EventType eventType,
        Long serverId,
        String reason,
        Instant createdAt
) {

    public static McpRuntimeRefreshMessage serverRefresh(String sourceInstanceId, Long serverId, String reason) {
        return new McpRuntimeRefreshMessage(sourceInstanceId, EventType.SERVER_REFRESH, serverId, reason,
                Instant.now());
    }

    public static McpRuntimeRefreshMessage serverDisable(String sourceInstanceId, Long serverId, String reason) {
        return new McpRuntimeRefreshMessage(sourceInstanceId, EventType.SERVER_DISABLE, serverId, reason,
                Instant.now());
    }

    public static McpRuntimeRefreshMessage allRefresh(String sourceInstanceId, String reason) {
        return new McpRuntimeRefreshMessage(sourceInstanceId, EventType.ALL_REFRESH, null, reason, Instant.now());
    }

    public enum EventType {
        SERVER_REFRESH,
        SERVER_DISABLE,
        ALL_REFRESH
    }

}
