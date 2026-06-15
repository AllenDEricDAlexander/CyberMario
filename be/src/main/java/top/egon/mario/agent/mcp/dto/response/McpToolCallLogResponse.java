package top.egon.mario.agent.mcp.dto.response;

import top.egon.mario.agent.mcp.po.enums.McpToolCallStatus;

import java.time.Instant;

/**
 * Persisted MCP tool call log returned to admin clients.
 */
public record McpToolCallLogResponse(
        Long id,
        String traceId,
        String threadId,
        Long userId,
        String serverCode,
        String toolKey,
        String toolName,
        String requestArgsSummary,
        String responseSummary,
        McpToolCallStatus status,
        String errorMsg,
        long costMs,
        Instant createdAt
) {
}
