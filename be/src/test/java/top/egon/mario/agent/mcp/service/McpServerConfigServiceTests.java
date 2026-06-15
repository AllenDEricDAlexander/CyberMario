package top.egon.mario.agent.mcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.egon.mario.agent.mcp.dto.request.CreateMcpServerRequest;
import top.egon.mario.agent.mcp.dto.request.UpdateMcpServerRequest;
import top.egon.mario.agent.mcp.dto.response.McpServerResponse;
import top.egon.mario.agent.mcp.po.McpServerConfigPo;
import top.egon.mario.agent.mcp.po.McpToolConfigPo;
import top.egon.mario.agent.mcp.po.enums.McpServerStatus;
import top.egon.mario.agent.mcp.po.enums.McpTransportType;
import top.egon.mario.agent.mcp.repository.McpServerConfigRepository;
import top.egon.mario.agent.mcp.repository.McpToolConfigRepository;
import top.egon.mario.agent.service.AgentException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifies MCP server configuration management.
 */
class McpServerConfigServiceTests {

    @Test
    void createServerStoresDisabledConfigAndMasksHeadersInResponse() {
        McpServerConfigRepository serverRepository = mock(McpServerConfigRepository.class);
        McpToolConfigRepository toolRepository = mock(McpToolConfigRepository.class);
        given(serverRepository.save(any(McpServerConfigPo.class))).willAnswer(invocation -> {
            McpServerConfigPo po = invocation.getArgument(0);
            po.setId(9L);
            po.setCreatedAt(Instant.parse("2026-06-14T01:00:00Z"));
            po.setUpdatedAt(Instant.parse("2026-06-14T01:00:00Z"));
            return po;
        });
        McpServerConfigService service = new McpServerConfigService(serverRepository, toolRepository, new ObjectMapper());
        CreateMcpServerRequest request = new CreateMcpServerRequest(
                "docs",
                "Docs MCP",
                McpTransportType.STREAMABLE_HTTP,
                "https://example.com",
                "/mcp",
                Map.of("Authorization", "Bearer secret-token"),
                null,
                null);

        McpServerResponse response = service.create(request, 7L);

        assertThat(response.enabled()).isFalse();
        assertThat(response.status()).isEqualTo(McpServerStatus.DISABLED);
        assertThat(response.connectTimeoutMs()).isEqualTo(5000);
        assertThat(response.requestTimeoutMs()).isEqualTo(30000);
        assertThat(response.headers()).containsEntry("Authorization", "Bearer s********");
        ArgumentCaptor<McpServerConfigPo> captor = ArgumentCaptor.forClass(McpServerConfigPo.class);
        verify(serverRepository).save(captor.capture());
        McpServerConfigPo saved = captor.getValue();
        assertThat(saved.getServerCode()).isEqualTo("docs");
        assertThat(saved.getCreatedBy()).isEqualTo(7L);
        assertThat(saved.getUpdatedBy()).isEqualTo(7L);
        assertThat(saved.getHeadersJson()).contains("secret-token");
    }

    @Test
    void createRejectsDuplicateActiveServerCodeAfterNormalize() {
        McpServerConfigRepository serverRepository = mock(McpServerConfigRepository.class);
        given(serverRepository.existsByServerCodeAndDeletedFalse("docs")).willReturn(true);
        McpServerConfigService service = newService(serverRepository, mock(McpToolConfigRepository.class));
        CreateMcpServerRequest request = new CreateMcpServerRequest(
                " Docs ",
                "Docs MCP",
                McpTransportType.STREAMABLE_HTTP,
                "https://example.com",
                "/mcp",
                null,
                null,
                null);

        assertThatThrownBy(() -> service.create(request, 7L))
                .isInstanceOf(AgentException.class)
                .extracting("code")
                .isEqualTo("AGENT_MCP_SERVER_CODE_EXISTS");
    }

    @Test
    void updateTrimsValuesAndKeepsTimeoutsAndHeaders() {
        McpServerConfigRepository serverRepository = mock(McpServerConfigRepository.class);
        McpServerConfigPo server = server("docs", true, McpServerStatus.CONNECTED);
        server.setHeadersJson("{\"Authorization\":\"old-token\"}");
        server.setConnectTimeoutMs(6000);
        server.setRequestTimeoutMs(31000);
        given(serverRepository.findByIdAndDeletedFalse(9L)).willReturn(Optional.of(server));
        given(serverRepository.save(any(McpServerConfigPo.class))).willAnswer(invocation -> invocation.getArgument(0));
        McpServerConfigService service = newService(serverRepository, mock(McpToolConfigRepository.class));
        UpdateMcpServerRequest request = new UpdateMcpServerRequest(
                " Docs MCP  ",
                McpTransportType.SSE,
                " https://example.org ",
                " /events ",
                null,
                null,
                45000);

        McpServerResponse response = service.update(9L, request, 8L);

        assertThat(response.serverName()).isEqualTo("Docs MCP");
        assertThat(response.baseUrl()).isEqualTo("https://example.org");
        assertThat(response.endpoint()).isEqualTo("/events");
        assertThat(response.transportType()).isEqualTo(McpTransportType.SSE);
        assertThat(response.headers()).containsEntry("Authorization", "old-toke********");
        assertThat(response.connectTimeoutMs()).isEqualTo(6000);
        assertThat(response.requestTimeoutMs()).isEqualTo(45000);
        assertThat(server.getUpdatedBy()).isEqualTo(8L);
    }

    @Test
    void updateReplacesHeadersWhenProvided() {
        McpServerConfigRepository serverRepository = mock(McpServerConfigRepository.class);
        McpServerConfigPo server = server("docs", true, McpServerStatus.CONNECTED);
        server.setHeadersJson("{\"Authorization\":\"old-token\"}");
        given(serverRepository.findByIdAndDeletedFalse(9L)).willReturn(Optional.of(server));
        given(serverRepository.save(any(McpServerConfigPo.class))).willAnswer(invocation -> invocation.getArgument(0));
        McpServerConfigService service = newService(serverRepository, mock(McpToolConfigRepository.class));
        UpdateMcpServerRequest request = new UpdateMcpServerRequest(
                "Docs MCP",
                McpTransportType.STREAMABLE_HTTP,
                "https://example.com",
                "/mcp",
                Map.of("X-Token", "abc"),
                7000,
                45000);

        McpServerResponse response = service.update(9L, request, 8L);

        assertThat(response.headers()).containsEntry("X-Token", "********");
        assertThat(response.connectTimeoutMs()).isEqualTo(7000);
        assertThat(response.requestTimeoutMs()).isEqualTo(45000);
        assertThat(server.getHeadersJson()).contains("abc").doesNotContain("old-token");
    }

    @Test
    void updatePreservesExistingHeaderWhenProvidedValueIsUnchangedMask() {
        McpServerConfigRepository serverRepository = mock(McpServerConfigRepository.class);
        McpServerConfigPo server = server("docs", true, McpServerStatus.CONNECTED);
        server.setHeadersJson("{\"Authorization\":\"Bearer real-token\",\"X-Api-Key\":\"real-secret\"}");
        given(serverRepository.findByIdAndDeletedFalse(9L)).willReturn(Optional.of(server));
        given(serverRepository.save(any(McpServerConfigPo.class))).willAnswer(invocation -> invocation.getArgument(0));
        McpServerConfigService service = newService(serverRepository, mock(McpToolConfigRepository.class));
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer changed-token");
        headers.put("X-Api-Key", "real-sec********");
        UpdateMcpServerRequest request = new UpdateMcpServerRequest(
                "Docs MCP",
                McpTransportType.STREAMABLE_HTTP,
                "https://example.com",
                "/mcp",
                headers,
                7000,
                45000);

        service.update(9L, request, 8L);

        assertThat(server.getHeadersJson()).contains("Bearer changed-token")
                .contains("real-secret")
                .doesNotContain("real-sec********")
                .doesNotContain("Bearer real-token");
    }

    @Test
    void enableDisableAndDeleteUpdateServerFieldsAndActor() {
        McpServerConfigRepository serverRepository = mock(McpServerConfigRepository.class);
        McpToolConfigRepository toolRepository = mock(McpToolConfigRepository.class);
        McpServerConfigPo server = server("docs", false, McpServerStatus.DISABLED);
        given(serverRepository.findByIdAndDeletedFalse(9L)).willReturn(Optional.of(server));
        given(serverRepository.save(any(McpServerConfigPo.class))).willAnswer(invocation -> invocation.getArgument(0));
        McpToolConfigPo tool = tool(9L, "docs_search");
        given(toolRepository.findByServerIdAndDeletedFalseOrderByIdAsc(9L)).willReturn(List.of(tool));
        McpServerConfigService service = newService(serverRepository, toolRepository);

        service.enable(9L, 7L);

        assertThat(server.isEnabled()).isTrue();
        assertThat(server.getStatus()).isEqualTo(McpServerStatus.CONNECTING);
        assertThat(server.getUpdatedBy()).isEqualTo(7L);

        service.disable(9L, 8L);

        assertThat(server.isEnabled()).isFalse();
        assertThat(server.getStatus()).isEqualTo(McpServerStatus.DISABLED);
        assertThat(server.getUpdatedBy()).isEqualTo(8L);

        service.delete(9L, 9L);

        assertThat(server.isDeleted()).isTrue();
        assertThat(server.getUpdatedBy()).isEqualTo(9L);
        assertThat(tool.isDeleted()).isTrue();
        assertThat(tool.getUpdatedBy()).isEqualTo(9L);
    }

    private McpServerConfigService newService(McpServerConfigRepository serverRepository,
                                              McpToolConfigRepository toolRepository) {
        return new McpServerConfigService(serverRepository, toolRepository, new ObjectMapper());
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
        server.setConnectTimeoutMs(5000);
        server.setRequestTimeoutMs(30000);
        server.setCreatedAt(Instant.parse("2026-06-14T01:00:00Z"));
        server.setUpdatedAt(Instant.parse("2026-06-14T01:00:00Z"));
        return server;
    }

    private McpToolConfigPo tool(Long serverId, String toolKey) {
        McpToolConfigPo tool = new McpToolConfigPo();
        tool.setId(10L);
        tool.setServerId(serverId);
        tool.setToolName("search");
        tool.setToolKey(toolKey);
        tool.setDisplayName(toolKey);
        tool.setLastDiscoveredAt(Instant.parse("2026-06-14T01:00:00Z"));
        return tool;
    }

}
