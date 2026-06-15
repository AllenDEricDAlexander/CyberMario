package top.egon.mario.agent.mcp.runtime;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * Provides the current dynamic MCP tools to agent runtime wiring.
 */
@Component
@RequiredArgsConstructor
public class McpAgentToolProvider {

    private final McpRuntimeRegistry registry;

    public ToolCallback[] currentToolCallbacks() {
        return registry.currentToolCallbacks();
    }

}
