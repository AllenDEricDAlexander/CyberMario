package top.egon.mario.agent.mcp.dto.response;

import top.egon.mario.agent.mcp.po.enums.McpToolRiskLevel;
import top.egon.mario.agent.mcp.po.enums.McpToolRuntimeStatus;

import java.time.Instant;

/**
 * Discovered MCP tool configuration returned to admin clients.
 */
public record McpToolResponse(
        Long id,
        Long serverId,
        String serverCode,
        String toolName,
        String toolKey,
        String displayName,
        String description,
        String inputSchemaJson,
        boolean enabled,
        McpToolRiskLevel riskLevel,
        boolean readonly,
        boolean requireConfirm,
        McpToolRuntimeStatus runtimeStatus,
        Instant lastDiscoveredAt
) {
}
