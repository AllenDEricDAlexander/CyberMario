package top.egon.mario.agent.mcp.dto.response;

import top.egon.mario.agent.mcp.po.enums.McpServerStatus;
import top.egon.mario.agent.mcp.po.enums.McpTransportType;

import java.time.Instant;
import java.util.Map;

/**
 * Managed MCP server configuration returned to admin clients.
 */
public record McpServerResponse(
        Long id,
        String serverCode,
        String serverName,
        McpTransportType transportType,
        String baseUrl,
        String endpoint,
        Map<String, String> headers,
        boolean enabled,
        int connectTimeoutMs,
        int requestTimeoutMs,
        McpServerStatus status,
        String lastError,
        Instant lastConnectedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
