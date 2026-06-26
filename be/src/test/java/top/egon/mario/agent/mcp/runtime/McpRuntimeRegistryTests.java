package top.egon.mario.agent.mcp.runtime;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import top.egon.mario.agent.mcp.po.McpServerConfigPo;
import top.egon.mario.agent.mcp.po.McpToolConfigPo;
import top.egon.mario.agent.mcp.po.enums.McpServerStatus;
import top.egon.mario.agent.mcp.po.enums.McpToolRiskLevel;
import top.egon.mario.agent.mcp.po.enums.McpTransportType;
import top.egon.mario.agent.mcp.policy.McpToolPolicyService;
import top.egon.mario.agent.mcp.repository.McpServerConfigRepository;
import top.egon.mario.agent.mcp.repository.McpToolConfigRepository;
import top.egon.mario.agent.mcp.service.McpToolCallLogService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Verifies MCP runtime registry filters persisted tools before exposing them to agents.
 */
class McpRuntimeRegistryTests {

    @Test
    void visibleEnabledConnectedToolReturnsLoggingCallbackWithToolKeyName() {
        TestSupport support = new TestSupport();
        McpServerConfigPo server = server(9L, true, McpServerStatus.CONNECTED);
        McpToolConfigPo tool = tool(9L, "search", "docs_search", true, false);
        McpSyncClient client = support.clientWithTools(runtimeTool("search"));
        given(support.toolRepository.findByEnabledTrueAndDeletedFalseOrderByIdAsc()).willReturn(List.of(tool));
        given(support.serverRepository.findByIdAndDeletedFalse(9L)).willReturn(Optional.of(server));
        given(support.clientManager.client(9L)).willReturn(Optional.of(client));

        ToolCallback[] callbacks = support.registry.currentToolCallbacks();

        assertThat(callbacks).hasSize(1);
        assertThat(callbacks[0]).isInstanceOf(LoggingMcpToolCallback.class);
        assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("docs_search");
    }

    @Test
    void filtersToolsThatRequireConfirmationOrHaveUnavailableServerOrRuntimeClient() {
        TestSupport support = new TestSupport();
        McpToolConfigPo requireConfirm = tool(1L, "search", "confirm_search", true, true);
        McpToolConfigPo serverDisabled = tool(2L, "search", "disabled_search", true, false);
        McpToolConfigPo serverFailed = tool(3L, "search", "failed_search", true, false);
        McpToolConfigPo missingClient = tool(4L, "search", "missing_client_search", true, false);
        McpToolConfigPo missingRuntimeTool = tool(5L, "search", "missing_runtime_search", true, false);
        given(support.toolRepository.findByEnabledTrueAndDeletedFalseOrderByIdAsc())
                .willReturn(List.of(requireConfirm, serverDisabled, serverFailed, missingClient, missingRuntimeTool));
        given(support.serverRepository.findByIdAndDeletedFalse(1L))
                .willReturn(Optional.of(server(1L, true, McpServerStatus.CONNECTED)));
        given(support.serverRepository.findByIdAndDeletedFalse(2L))
                .willReturn(Optional.of(server(2L, false, McpServerStatus.CONNECTED)));
        given(support.serverRepository.findByIdAndDeletedFalse(3L))
                .willReturn(Optional.of(server(3L, true, McpServerStatus.FAILED)));
        given(support.serverRepository.findByIdAndDeletedFalse(4L))
                .willReturn(Optional.of(server(4L, true, McpServerStatus.CONNECTED)));
        given(support.serverRepository.findByIdAndDeletedFalse(5L))
                .willReturn(Optional.of(server(5L, true, McpServerStatus.CONNECTED)));
        McpSyncClient clientWithoutMatchingTool = support.clientWithTools(runtimeTool("other"));
        given(support.clientManager.client(5L)).willReturn(Optional.of(clientWithoutMatchingTool));

        ToolCallback[] callbacks = support.registry.currentToolCallbacks();

        assertThat(callbacks).isEmpty();
    }

    @Test
    void registryDoesNotExposeEnabledToolsIfParentServerIsAbsentOrDeleted() {
        TestSupport support = new TestSupport();
        McpToolConfigPo tool = tool(9L, "search", "docs_search", true, false);
        given(support.toolRepository.findByEnabledTrueAndDeletedFalseOrderByIdAsc()).willReturn(List.of(tool));
        given(support.serverRepository.findByIdAndDeletedFalse(9L)).willReturn(Optional.empty());

        ToolCallback[] callbacks = support.registry.currentToolCallbacks();

        assertThat(callbacks).isEmpty();
    }

    @Test
    void registryReflectsCurrentClientManagerSnapshotAcrossCalls() {
        TestSupport support = new TestSupport();
        McpServerConfigPo server = server(9L, true, McpServerStatus.CONNECTED);
        McpToolConfigPo tool = tool(9L, "search", "docs_search", true, false);
        McpSyncClient firstClient = support.clientWithTools(runtimeTool("other"));
        McpSyncClient refreshedClient = support.clientWithTools(runtimeTool("search"));
        given(support.toolRepository.findByEnabledTrueAndDeletedFalseOrderByIdAsc()).willReturn(List.of(tool));
        given(support.serverRepository.findByIdAndDeletedFalse(9L)).willReturn(Optional.of(server));
        given(support.clientManager.client(9L)).willReturn(Optional.of(firstClient), Optional.of(refreshedClient));

        ToolCallback[] beforeRefresh = support.registry.currentToolCallbacks();
        ToolCallback[] afterRefresh = support.registry.currentToolCallbacks();

        assertThat(beforeRefresh).isEmpty();
        assertThat(afterRefresh).hasSize(1);
        assertThat(afterRefresh[0].getToolDefinition().name()).isEqualTo("docs_search");
    }

    @Test
    void listToolsFailureSkipsOnlyThatServer() {
        TestSupport support = new TestSupport();
        McpToolConfigPo failedTool = tool(1L, "search", "failed_search", true, false);
        McpToolConfigPo availableTool = tool(2L, "search", "available_search", true, false);
        McpSyncClient failedClient = mock(McpSyncClient.class);
        McpSyncClient availableClient = support.clientWithTools(runtimeTool("search"));
        given(failedClient.listTools()).willThrow(new RuntimeException("list failed"));
        given(support.toolRepository.findByEnabledTrueAndDeletedFalseOrderByIdAsc())
                .willReturn(List.of(failedTool, availableTool));
        given(support.serverRepository.findByIdAndDeletedFalse(1L))
                .willReturn(Optional.of(server(1L, true, McpServerStatus.CONNECTED)));
        given(support.serverRepository.findByIdAndDeletedFalse(2L))
                .willReturn(Optional.of(server(2L, true, McpServerStatus.CONNECTED)));
        given(support.clientManager.client(1L)).willReturn(Optional.of(failedClient));
        given(support.clientManager.client(2L)).willReturn(Optional.of(availableClient));

        ToolCallback[] callbacks = support.registry.currentToolCallbacks();

        assertThat(callbacks).hasSize(1);
        assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("available_search");
    }

    @Test
    void multipleToolsOnSameServerListRuntimeToolsOnce() {
        TestSupport support = new TestSupport();
        McpToolConfigPo searchTool = tool(9L, "search", "docs_search", true, false);
        McpToolConfigPo readTool = tool(9L, "read", "docs_read", true, false);
        McpSyncClient client = support.clientWithTools(runtimeTool("search"), runtimeTool("read"));
        McpServerConfigPo server = server(9L, true, McpServerStatus.CONNECTED);
        given(support.toolRepository.findByEnabledTrueAndDeletedFalseOrderByIdAsc())
                .willReturn(List.of(searchTool, readTool));
        given(support.serverRepository.findByIdAndDeletedFalse(9L)).willReturn(Optional.of(server));
        given(support.clientManager.client(9L)).willReturn(Optional.of(client));

        ToolCallback[] callbacks = support.registry.currentToolCallbacks();

        assertThat(callbacks).hasSize(2);
        verify(client, times(1)).listTools();
    }

    private McpServerConfigPo server(Long id, boolean enabled, McpServerStatus status) {
        McpServerConfigPo server = new McpServerConfigPo();
        server.setId(id);
        server.setServerCode("docs-" + id);
        server.setServerName("Docs MCP");
        server.setTransportType(McpTransportType.STREAMABLE_HTTP);
        server.setBaseUrl("https://example.com");
        server.setEndpoint("/mcp");
        server.setEnabled(enabled);
        server.setStatus(status);
        return server;
    }

    private McpToolConfigPo tool(Long serverId, String toolName, String toolKey, boolean enabled,
                                 boolean requireConfirm) {
        McpToolConfigPo tool = new McpToolConfigPo();
        tool.setId(serverId * 10);
        tool.setServerId(serverId);
        tool.setToolName(toolName);
        tool.setToolKey(toolKey);
        tool.setDisplayName(toolKey);
        tool.setDescription("Search docs");
        tool.setInputSchemaJson("{\"type\":\"object\"}");
        tool.setEnabled(enabled);
        tool.setRiskLevel(McpToolRiskLevel.MEDIUM);
        tool.setReadonly(true);
        tool.setRequireConfirm(requireConfirm);
        tool.setLastDiscoveredAt(Instant.parse("2026-06-14T01:00:00Z"));
        return tool;
    }

    private McpSchema.Tool runtimeTool(String name) {
        return McpSchema.Tool.builder()
                .name(name)
                .description("Search docs")
                .inputSchema(new McpSchema.JsonSchema("object", Map.of(), List.of(), true, Map.of(), Map.of()))
                .build();
    }

    private class TestSupport {
        private final McpToolConfigRepository toolRepository = mock(McpToolConfigRepository.class);
        private final McpServerConfigRepository serverRepository = mock(McpServerConfigRepository.class);
        private final DynamicMcpClientManager clientManager = mock(DynamicMcpClientManager.class);
        private final McpToolCallLogService logService = mock(McpToolCallLogService.class);
        private final McpRuntimeRegistry registry = new McpRuntimeRegistry(
                toolRepository,
                serverRepository,
                clientManager,
                new McpToolPolicyService(),
                logService);

        private McpSyncClient clientWithTools(McpSchema.Tool... tools) {
            McpSyncClient client = mock(McpSyncClient.class);
            given(client.listTools()).willReturn(new McpSchema.ListToolsResult(List.of(tools), null));
            return client;
        }
    }

}
