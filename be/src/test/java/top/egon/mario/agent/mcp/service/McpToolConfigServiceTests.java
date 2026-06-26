package top.egon.mario.agent.mcp.service;

import org.junit.jupiter.api.Test;
import top.egon.mario.agent.mcp.dto.request.UpdateMcpToolPolicyRequest;
import top.egon.mario.agent.mcp.dto.response.McpToolResponse;
import top.egon.mario.agent.mcp.po.McpServerConfigPo;
import top.egon.mario.agent.mcp.po.McpToolConfigPo;
import top.egon.mario.agent.mcp.po.enums.McpServerStatus;
import top.egon.mario.agent.mcp.po.enums.McpToolRiskLevel;
import top.egon.mario.agent.mcp.po.enums.McpToolRuntimeStatus;
import top.egon.mario.agent.mcp.po.enums.McpTransportType;
import top.egon.mario.agent.mcp.repository.McpServerConfigRepository;
import top.egon.mario.agent.mcp.repository.McpToolConfigRepository;
import top.egon.mario.agent.service.AgentException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Verifies MCP tool policy management and derived runtime status.
 */
class McpToolConfigServiceTests {

    @Test
    void enableToolRejectsToolThatRequiresConfirmation() {
        McpServerConfigRepository serverRepository = mock(McpServerConfigRepository.class);
        McpToolConfigRepository toolRepository = mock(McpToolConfigRepository.class);
        McpServerConfigPo server = server("docs", true, McpServerStatus.CONNECTED);
        McpToolConfigPo tool = tool(server.getId(), "docs_search", true);
        given(toolRepository.findByIdAndDeletedFalse(10L)).willReturn(Optional.of(tool));
        given(serverRepository.findByIdAndDeletedFalse(server.getId())).willReturn(Optional.of(server));
        McpToolConfigService service = new McpToolConfigService(toolRepository, serverRepository);

        assertThatThrownBy(() -> service.enable(10L, 7L))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("requires confirmation");
    }

    @Test
    void runtimeStatusDerivationCoversAllStates() {
        McpServerConfigRepository serverRepository = mock(McpServerConfigRepository.class);
        McpToolConfigRepository toolRepository = mock(McpToolConfigRepository.class);
        McpToolConfigService service = new McpToolConfigService(toolRepository, serverRepository);
        McpToolConfigPo tool = tool(9L, "docs_search", false);
        given(toolRepository.findByServerIdAndDeletedFalseOrderByIdAsc(9L)).willReturn(List.of(tool));
        given(serverRepository.findByIdAndDeletedFalse(9L))
                .willReturn(Optional.of(server("docs", true, McpServerStatus.CONNECTED)));

        tool.setEnabled(false);
        assertThat(service.list(9L).get(0).runtimeStatus()).isEqualTo(McpToolRuntimeStatus.DISABLED);

        tool.setEnabled(true);
        given(serverRepository.findByIdAndDeletedFalse(9L))
                .willReturn(Optional.of(server("docs", false, McpServerStatus.CONNECTED)));
        assertThat(service.list(9L).get(0).runtimeStatus()).isEqualTo(McpToolRuntimeStatus.SERVER_DISABLED);

        given(serverRepository.findByIdAndDeletedFalse(9L))
                .willReturn(Optional.of(server("docs", true, McpServerStatus.FAILED)));
        assertThat(service.list(9L).get(0).runtimeStatus()).isEqualTo(McpToolRuntimeStatus.SERVER_FAILED);

        tool.setRequireConfirm(true);
        given(serverRepository.findByIdAndDeletedFalse(9L))
                .willReturn(Optional.of(server("docs", true, McpServerStatus.CONNECTED)));
        assertThat(service.list(9L).get(0).runtimeStatus()).isEqualTo(McpToolRuntimeStatus.POLICY_BLOCKED);

        tool.setRequireConfirm(false);
        assertThat(service.list(9L).get(0).runtimeStatus()).isEqualTo(McpToolRuntimeStatus.AVAILABLE);
    }

    @Test
    void updateToolPolicySetsPolicyAndDisablesWhenConfirmationIsRequired() {
        McpServerConfigRepository serverRepository = mock(McpServerConfigRepository.class);
        McpToolConfigRepository toolRepository = mock(McpToolConfigRepository.class);
        McpServerConfigPo server = server("docs", true, McpServerStatus.CONNECTED);
        McpToolConfigPo tool = tool(server.getId(), "docs_search", false);
        tool.setEnabled(true);
        given(toolRepository.findByIdAndDeletedFalse(10L)).willReturn(Optional.of(tool));
        given(toolRepository.save(any(McpToolConfigPo.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(serverRepository.findByIdAndDeletedFalse(server.getId())).willReturn(Optional.of(server));
        McpToolConfigService service = new McpToolConfigService(toolRepository, serverRepository);
        UpdateMcpToolPolicyRequest request = new UpdateMcpToolPolicyRequest(McpToolRiskLevel.HIGH, true, true);

        McpToolResponse response = service.updatePolicy(10L, request, 7L);

        assertThat(response.riskLevel()).isEqualTo(McpToolRiskLevel.HIGH);
        assertThat(response.readonly()).isTrue();
        assertThat(response.requireConfirm()).isTrue();
        assertThat(response.enabled()).isFalse();
        assertThat(response.runtimeStatus()).isEqualTo(McpToolRuntimeStatus.DISABLED);
        assertThat(tool.getUpdatedBy()).isEqualTo(7L);
    }

    @Test
    void updateToolPolicyAllowsEnabledToolWhenConfirmationIsNotRequired() {
        McpServerConfigRepository serverRepository = mock(McpServerConfigRepository.class);
        McpToolConfigRepository toolRepository = mock(McpToolConfigRepository.class);
        McpServerConfigPo server = server("docs", true, McpServerStatus.CONNECTED);
        McpToolConfigPo tool = tool(server.getId(), "docs_search", true);
        tool.setEnabled(true);
        given(toolRepository.findByIdAndDeletedFalse(10L)).willReturn(Optional.of(tool));
        given(toolRepository.save(any(McpToolConfigPo.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(serverRepository.findByIdAndDeletedFalse(server.getId())).willReturn(Optional.of(server));
        McpToolConfigService service = new McpToolConfigService(toolRepository, serverRepository);
        UpdateMcpToolPolicyRequest request = new UpdateMcpToolPolicyRequest(McpToolRiskLevel.LOW, true, false);

        McpToolResponse response = service.updatePolicy(10L, request, 7L);

        assertThat(response.enabled()).isTrue();
        assertThat(response.requireConfirm()).isFalse();
        assertThat(response.runtimeStatus()).isEqualTo(McpToolRuntimeStatus.AVAILABLE);
    }

    @Test
    void enableAndDisableToolUpdateFieldsAndActor() {
        McpServerConfigRepository serverRepository = mock(McpServerConfigRepository.class);
        McpToolConfigRepository toolRepository = mock(McpToolConfigRepository.class);
        McpServerConfigPo server = server("docs", true, McpServerStatus.CONNECTED);
        McpToolConfigPo tool = tool(server.getId(), "docs_search", false);
        given(toolRepository.findByIdAndDeletedFalse(10L)).willReturn(Optional.of(tool));
        given(toolRepository.save(any(McpToolConfigPo.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(serverRepository.findByIdAndDeletedFalse(server.getId())).willReturn(Optional.of(server));
        McpToolConfigService service = new McpToolConfigService(toolRepository, serverRepository);

        McpToolResponse enabled = service.enable(10L, 7L);

        assertThat(enabled.serverId()).isEqualTo(9L);
        assertThat(enabled.enabled()).isTrue();
        assertThat(enabled.runtimeStatus()).isEqualTo(McpToolRuntimeStatus.AVAILABLE);
        assertThat(tool.isEnabled()).isTrue();
        assertThat(tool.getUpdatedBy()).isEqualTo(7L);

        McpToolResponse disabled = service.disable(10L, 8L);

        assertThat(disabled.serverId()).isEqualTo(9L);
        assertThat(disabled.enabled()).isFalse();
        assertThat(disabled.runtimeStatus()).isEqualTo(McpToolRuntimeStatus.DISABLED);
        assertThat(tool.isEnabled()).isFalse();
        assertThat(tool.getUpdatedBy()).isEqualTo(8L);
    }

    private McpServerConfigPo server(String serverCode, boolean enabled, McpServerStatus status) {
        McpServerConfigPo server = new McpServerConfigPo();
        server.setId(9L);
        server.setServerCode(serverCode);
        server.setServerName("Docs MCP");
        server.setTransportType(McpTransportType.STREAMABLE_HTTP);
        server.setBaseUrl("https://example.com");
        server.setEndpoint("/mcp");
        server.setEnabled(enabled);
        server.setStatus(status);
        server.setCreatedAt(Instant.parse("2026-06-14T01:00:00Z"));
        server.setUpdatedAt(Instant.parse("2026-06-14T01:00:00Z"));
        return server;
    }

    private McpToolConfigPo tool(Long serverId, String toolKey, boolean requireConfirm) {
        McpToolConfigPo tool = new McpToolConfigPo();
        tool.setId(10L);
        tool.setServerId(serverId);
        tool.setToolName("search");
        tool.setToolKey(toolKey);
        tool.setDisplayName(toolKey);
        tool.setDescription("Search docs");
        tool.setInputSchemaJson("{\"type\":\"object\"}");
        tool.setRiskLevel(McpToolRiskLevel.MEDIUM);
        tool.setReadonly(false);
        tool.setRequireConfirm(requireConfirm);
        tool.setLastDiscoveredAt(Instant.parse("2026-06-14T01:00:00Z"));
        return tool;
    }

}
