package top.egon.mario.agent.mcp.dto.response;

/**
 * Result returned after testing connectivity to an MCP server.
 */
public record McpConnectionTestResponse(
        boolean success,
        String serverName,
        String serverVersion,
        int toolCount,
        String errorMessage
) {
}
