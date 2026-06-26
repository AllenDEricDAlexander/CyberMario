package top.egon.mario.agent.mcp.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis Pub/Sub settings for MCP runtime refresh broadcasts.
 */
@ConfigurationProperties(prefix = "agent.mcp.runtime.refresh")
public record McpRuntimeRefreshProperties(
        Boolean broadcastEnabled,
        String broadcastTopic
) {

    public McpRuntimeRefreshProperties {
        broadcastEnabled = broadcastEnabled == null || broadcastEnabled;
        broadcastTopic = broadcastTopic == null || broadcastTopic.isBlank()
                ? "agent:mcp:runtime:refresh"
                : broadcastTopic;
    }

}
