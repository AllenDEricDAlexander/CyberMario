package top.egon.mario.agent.mcp.runtime;

import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.Test;
import top.egon.mario.agent.mcp.po.McpServerConfigPo;
import top.egon.mario.agent.mcp.po.enums.McpServerStatus;
import top.egon.mario.agent.mcp.po.enums.McpTransportType;
import top.egon.mario.agent.mcp.repository.McpServerConfigRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifies dynamic MCP client lifecycle behavior.
 */
class DynamicMcpClientManagerTests {

    @Test
    void refreshServerInstallsNewClientWhenExistingCloseFails() {
        McpServerConfigRepository serverRepository = mock(McpServerConfigRepository.class);
        McpClientFactory clientFactory = mock(McpClientFactory.class);
        McpSyncClient oldClient = mock(McpSyncClient.class);
        McpSyncClient newClient = mock(McpSyncClient.class);
        McpServerConfigPo server = server();
        given(serverRepository.findByIdAndDeletedFalse(9L)).willReturn(Optional.of(server));
        given(serverRepository.save(any(McpServerConfigPo.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(clientFactory.create(server)).willReturn(oldClient, newClient);
        DynamicMcpClientManager manager = new DynamicMcpClientManager(serverRepository, clientFactory);
        manager.refreshServer(9L);
        willThrow(new RuntimeException("graceful close failed")).given(oldClient).closeGracefully();
        willThrow(new RuntimeException("close failed")).given(oldClient).close();

        manager.refreshServer(9L);

        assertThat(manager.client(9L)).containsSame(newClient);
        assertThat(server.getStatus()).isEqualTo(McpServerStatus.CONNECTED);
        assertThat(server.getLastError()).isNull();
    }

    @Test
    void disableServerDisablesPersistenceWhenExistingCloseFails() {
        McpServerConfigRepository serverRepository = mock(McpServerConfigRepository.class);
        McpClientFactory clientFactory = mock(McpClientFactory.class);
        McpSyncClient oldClient = mock(McpSyncClient.class);
        McpServerConfigPo server = server();
        given(serverRepository.findByIdAndDeletedFalse(9L)).willReturn(Optional.of(server));
        given(serverRepository.save(any(McpServerConfigPo.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(clientFactory.create(server)).willReturn(oldClient);
        DynamicMcpClientManager manager = new DynamicMcpClientManager(serverRepository, clientFactory);
        manager.refreshServer(9L);
        willThrow(new RuntimeException("graceful close failed")).given(oldClient).closeGracefully();
        willThrow(new RuntimeException("close failed")).given(oldClient).close();

        manager.disableServer(9L);

        assertThat(manager.client(9L)).isEmpty();
        assertThat(server.isEnabled()).isFalse();
        assertThat(server.getStatus()).isEqualTo(McpServerStatus.DISABLED);
    }

    @Test
    void refreshServerClosesCreatedClientWhenPersistenceFailsBeforeInstall() {
        McpServerConfigRepository serverRepository = mock(McpServerConfigRepository.class);
        McpClientFactory clientFactory = mock(McpClientFactory.class);
        McpSyncClient client = mock(McpSyncClient.class);
        McpServerConfigPo server = server();
        given(serverRepository.findByIdAndDeletedFalse(9L)).willReturn(Optional.of(server));
        given(clientFactory.create(server)).willReturn(client);
        given(serverRepository.save(any(McpServerConfigPo.class))).willThrow(new RuntimeException("save failed"));
        DynamicMcpClientManager manager = new DynamicMcpClientManager(serverRepository, clientFactory);

        assertThatThrownBy(() -> manager.refreshServer(9L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("save failed");
        verify(client).closeGracefully();
    }

    private McpServerConfigPo server() {
        McpServerConfigPo server = new McpServerConfigPo();
        server.setId(9L);
        server.setServerCode("docs");
        server.setServerName("Docs MCP");
        server.setTransportType(McpTransportType.STREAMABLE_HTTP);
        server.setBaseUrl("https://example.com");
        server.setEndpoint("/mcp");
        server.setEnabled(true);
        server.setStatus(McpServerStatus.CONNECTING);
        return server;
    }

}
