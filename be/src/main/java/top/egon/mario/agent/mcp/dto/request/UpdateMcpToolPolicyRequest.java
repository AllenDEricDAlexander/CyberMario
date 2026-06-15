package top.egon.mario.agent.mcp.dto.request;

import jakarta.validation.constraints.NotNull;
import top.egon.mario.agent.mcp.po.enums.McpToolRiskLevel;

/**
 * Request body for updating MCP tool runtime policy.
 */
public record UpdateMcpToolPolicyRequest(
        @NotNull McpToolRiskLevel riskLevel,
        boolean readonly,
        boolean requireConfirm
) {
}
