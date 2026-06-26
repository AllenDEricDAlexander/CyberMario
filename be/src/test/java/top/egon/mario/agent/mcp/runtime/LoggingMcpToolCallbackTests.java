package top.egon.mario.agent.mcp.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import top.egon.mario.agent.mcp.po.McpServerConfigPo;
import top.egon.mario.agent.mcp.po.McpToolConfigPo;
import top.egon.mario.agent.mcp.service.McpToolCallLogService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * Verifies MCP tool audit logging does not change delegated tool behavior.
 */
class LoggingMcpToolCallbackTests {

    @Test
    void successLoggingFailureDoesNotMaskDelegateResponse() {
        ToolCallback delegate = mock(ToolCallback.class);
        McpToolCallLogService logService = mock(McpToolCallLogService.class);
        given(delegate.call("{}")).willReturn("ok");
        doThrow(new RuntimeException("log failed")).when(logService)
                .recordSuccess(any(), any(), eq("{}"), eq("ok"), anyLong(), eq(null));
        LoggingMcpToolCallback callback = new LoggingMcpToolCallback(delegate, server(), tool(), logService);

        String response = callback.call("{}", null);

        assertThat(response).isEqualTo("ok");
    }

    @Test
    void failureLoggingFailureDoesNotMaskDelegateException() {
        ToolCallback delegate = mock(ToolCallback.class);
        McpToolCallLogService logService = mock(McpToolCallLogService.class);
        RuntimeException delegateFailure = new RuntimeException("delegate failed");
        given(delegate.call("{}")).willThrow(delegateFailure);
        doThrow(new RuntimeException("log failed")).when(logService)
                .recordFailure(any(), any(), eq("{}"), eq(delegateFailure), anyLong(), eq(null));
        LoggingMcpToolCallback callback = new LoggingMcpToolCallback(delegate, server(), tool(), logService);

        assertThatThrownBy(() -> callback.call("{}", null))
                .isSameAs(delegateFailure)
                .satisfies(error -> assertThat(error.getSuppressed()).hasSize(1));
    }

    @Test
    void exposesMcpServerAndToolIdentityForRunAudit() {
        ToolCallback delegate = mock(ToolCallback.class);
        LoggingMcpToolCallback callback = new LoggingMcpToolCallback(delegate, server(), tool(),
                mock(McpToolCallLogService.class));

        assertThat(callback.serverCode()).isEqualTo("docs");
        assertThat(callback.toolKey()).isEqualTo("docs_search");
    }

    @Test
    void toolDefinitionDescriptionMarksMcpSourceForModel() {
        ToolCallback delegate = mock(ToolCallback.class);
        given(delegate.getToolDefinition()).willReturn(ToolDefinition.builder()
                .name("docs_search")
                .description("Search repository docs")
                .inputSchema("{}")
                .build());
        LoggingMcpToolCallback callback = new LoggingMcpToolCallback(delegate, server(), tool(),
                mock(McpToolCallLogService.class));

        ToolDefinition definition = callback.getToolDefinition();

        assertThat(definition.name()).isEqualTo("docs_search");
        assertThat(definition.inputSchema()).isEqualTo("{}");
        assertThat(definition.description())
                .contains("[MCP tool from server: docs]")
                .contains("Search repository docs");
    }

    private McpServerConfigPo server() {
        McpServerConfigPo server = new McpServerConfigPo();
        server.setServerCode("docs");
        return server;
    }

    private McpToolConfigPo tool() {
        McpToolConfigPo tool = new McpToolConfigPo();
        tool.setToolKey("docs_search");
        tool.setToolName("search");
        return tool;
    }

}
