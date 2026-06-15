package top.egon.mario.agent.mcp.dto.response;

/**
 * Summary returned after discovering tools from an MCP server.
 */
public record McpToolDiscoveryResponse(
        Long serverId,
        int discoveredCount,
        int createdCount,
        int updatedCount
) {
}
