package top.egon.mario.agent.mcp.runtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Identifies the current application instance in MCP runtime refresh broadcasts.
 */
@Component
@ConditionalOnProperty(prefix = "agent.mcp.runtime", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpRuntimeInstanceIdentity {

    private final String sourceInstanceId = UUID.randomUUID().toString();

    public String sourceInstanceId() {
        return sourceInstanceId;
    }

    public boolean isLocalSource(String sourceInstanceId) {
        return this.sourceInstanceId.equals(sourceInstanceId);
    }

}
