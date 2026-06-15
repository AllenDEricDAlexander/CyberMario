package top.egon.mario.agent.mcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import top.egon.mario.agent.mcp.dto.response.McpToolDiscoveryResponse;
import top.egon.mario.agent.mcp.po.McpServerConfigPo;
import top.egon.mario.agent.mcp.po.enums.McpServerStatus;
import top.egon.mario.agent.mcp.po.enums.McpTransportType;
import top.egon.mario.agent.mcp.repository.McpToolConfigRepository;
import top.egon.mario.agent.mcp.runtime.McpAgentRefreshService;
import top.egon.mario.agent.mcp.runtime.McpClientFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Verifies MCP tool discovery handles runtime client edge cases.
 */
class McpToolDiscoveryServiceTests {

    @Test
    void nullListToolsResultIsTreatedAsZeroDiscoveredTools() {
        TestSupport support = new TestSupport();
        McpSyncClient client = support.client();
        given(support.clientFactory.create(support.server)).willReturn(client);
        given(client.listTools()).willReturn(null);

        McpToolDiscoveryResponse response = support.service.discover(9L, 7L);

        assertThat(response.discoveredCount()).isZero();
        assertThat(response.createdCount()).isZero();
        assertThat(response.updatedCount()).isZero();
        assertThat(support.refreshService.version()).isEqualTo(1L);
        verifyNoInteractions(support.toolRepository);
    }

    @Test
    void nullToolsListIsTreatedAsZeroDiscoveredTools() {
        TestSupport support = new TestSupport();
        McpSyncClient client = support.client();
        given(support.clientFactory.create(support.server)).willReturn(client);
        given(client.listTools()).willReturn(new McpSchema.ListToolsResult(null, null));

        McpToolDiscoveryResponse response = support.service.discover(9L, 7L);

        assertThat(response.discoveredCount()).isZero();
        assertThat(response.createdCount()).isZero();
        assertThat(response.updatedCount()).isZero();
        assertThat(support.refreshService.version()).isEqualTo(1L);
        verifyNoInteractions(support.toolRepository);
    }

    @Test
    void closeFailureDoesNotMaskListToolsFailure() {
        TestSupport support = new TestSupport();
        McpSyncClient client = support.client();
        RuntimeException listFailure = new RuntimeException("list failed");
        given(support.clientFactory.create(support.server)).willReturn(client);
        given(client.listTools()).willThrow(listFailure);
        given(client.closeGracefully()).willThrow(new RuntimeException("close failed"));

        assertThatThrownBy(() -> support.service.discover(9L, 7L))
                .isSameAs(listFailure);
    }

    private static class TestSupport {
        private final McpServerConfigPo server = server();
        private final McpServerConfigService serverConfigService = mock(McpServerConfigService.class);
        private final McpToolConfigRepository toolRepository = mock(McpToolConfigRepository.class);
        private final McpClientFactory clientFactory = mock(McpClientFactory.class);
        private final McpAgentRefreshService refreshService = new McpAgentRefreshService();
        private final McpToolDiscoveryService service = new McpToolDiscoveryService(
                serverConfigService,
                toolRepository,
                clientFactory,
                new ObjectMapper(),
                refreshService);

        TestSupport() {
            given(serverConfigService.requireServer(9L)).willReturn(server);
        }

        private McpSyncClient client() {
            return mock(McpSyncClient.class);
        }

        private static McpServerConfigPo server() {
            McpServerConfigPo server = new McpServerConfigPo();
            server.setId(9L);
            server.setServerCode("docs");
            server.setServerName("Docs MCP");
            server.setTransportType(McpTransportType.STREAMABLE_HTTP);
            server.setBaseUrl("https://example.com");
            server.setEndpoint("/mcp");
            server.setEnabled(true);
            server.setStatus(McpServerStatus.CONNECTED);
            return server;
        }
    }

}
