package top.egon.mario.agent.mcp.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import top.egon.mario.agent.mcp.runtime.DynamicMcpClientManager;

/**
 * Initializes dynamic MCP runtime clients after the application is ready.
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "agent.mcp.runtime", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpManagementConfiguration {

    private final DynamicMcpClientManager clientManager;

    @EventListener(ApplicationReadyEvent.class)
    public void reloadEnabledServers() {
        clientManager.reloadEnabledServers();
    }

}
