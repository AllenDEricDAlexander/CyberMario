package top.egon.mario.agent.mcp.runtime;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight refresh marker for agent runtime MCP tool snapshots.
 */
@Service
public class McpAgentRefreshService {

    private final AtomicLong version = new AtomicLong();

    public void refresh() {
        version.incrementAndGet();
    }

    public long version() {
        return version.get();
    }

}
