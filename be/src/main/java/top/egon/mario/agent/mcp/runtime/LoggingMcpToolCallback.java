package top.egon.mario.agent.mcp.runtime;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import top.egon.mario.agent.mcp.po.McpServerConfigPo;
import top.egon.mario.agent.mcp.po.McpToolConfigPo;
import top.egon.mario.agent.mcp.service.McpToolCallLogService;

/**
 * Adds persisted audit logging around a delegated MCP tool callback.
 */
public class LoggingMcpToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final McpServerConfigPo server;
    private final McpToolConfigPo tool;
    private final McpToolCallLogService logService;

    public LoggingMcpToolCallback(ToolCallback delegate, McpServerConfigPo server, McpToolConfigPo tool,
                                  McpToolCallLogService logService) {
        this.delegate = delegate;
        this.server = server;
        this.tool = tool;
        this.logService = logService;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    public String serverCode() {
        return server == null ? null : server.getServerCode();
    }

    public String toolKey() {
        return tool == null ? null : tool.getToolKey();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        long startedAt = System.currentTimeMillis();
        try {
            String response = toolContext == null ? delegate.call(toolInput) : delegate.call(toolInput, toolContext);
            recordSuccess(toolInput, response, elapsed(startedAt), toolContext);
            return response;
        } catch (RuntimeException e) {
            recordFailure(toolInput, e, elapsed(startedAt), toolContext);
            throw e;
        }
    }

    private long elapsed(long startedAt) {
        return Math.max(0, System.currentTimeMillis() - startedAt);
    }

    private void recordSuccess(String toolInput, String response, long costMs, ToolContext toolContext) {
        try {
            logService.recordSuccess(server, tool, toolInput, response, costMs, toolContext);
        } catch (RuntimeException ignored) {
            // best effort
        }
    }

    private void recordFailure(String toolInput, RuntimeException original, long costMs, ToolContext toolContext) {
        try {
            logService.recordFailure(server, tool, toolInput, original, costMs, toolContext);
        } catch (RuntimeException loggingException) {
            original.addSuppressed(loggingException);
        }
    }

}
