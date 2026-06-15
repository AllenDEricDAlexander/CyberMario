package top.egon.mario.agent.mcp.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import top.egon.mario.agent.mcp.po.enums.McpTransportType;

import java.util.Map;

/**
 * Request body for updating an existing managed MCP server.
 */
public record UpdateMcpServerRequest(
        @NotBlank @Size(max = 128) String serverName,
        @NotNull McpTransportType transportType,
        @NotBlank @Size(max = 512) String baseUrl,
        @NotBlank @Size(max = 256) String endpoint,
        Map<String, String> headers,
        @Min(1000) @Max(60000) Integer connectTimeoutMs,
        @Min(1000) @Max(120000) Integer requestTimeoutMs
) {
}
