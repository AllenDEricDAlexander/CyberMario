package top.egon.mario.agent.mcp.runtime;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Verifies local MCP runtime refresh operations apply before broadcasting.
 */
class McpRuntimeRefreshCoordinatorTests {

    @Test
    void refreshServerAppliesLocalRefreshBeforeBroadcast() {
        DynamicMcpClientManager clientManager = mock(DynamicMcpClientManager.class);
        McpRuntimeRefreshBroadcaster broadcaster = mock(McpRuntimeRefreshBroadcaster.class);
        McpRuntimeRefreshCoordinator coordinator = new McpRuntimeRefreshCoordinator(clientManager, broadcaster);

        coordinator.refreshServer(9L, "tool_enable");

        InOrder inOrder = inOrder(clientManager, broadcaster);
        inOrder.verify(clientManager).refreshServer(9L);
        inOrder.verify(broadcaster).publishServerRefresh(9L, "tool_enable");
    }

    @Test
    void disableServerAppliesLocalDisableBeforeBroadcast() {
        DynamicMcpClientManager clientManager = mock(DynamicMcpClientManager.class);
        McpRuntimeRefreshBroadcaster broadcaster = mock(McpRuntimeRefreshBroadcaster.class);
        McpRuntimeRefreshCoordinator coordinator = new McpRuntimeRefreshCoordinator(clientManager, broadcaster);

        coordinator.disableServer(9L, "server_disable");

        InOrder inOrder = inOrder(clientManager, broadcaster);
        inOrder.verify(clientManager).disableServer(9L);
        inOrder.verify(broadcaster).publishServerDisable(9L, "server_disable");
    }

    @Test
    void refreshAllReloadsEnabledServersBeforeBroadcast() {
        DynamicMcpClientManager clientManager = mock(DynamicMcpClientManager.class);
        McpRuntimeRefreshBroadcaster broadcaster = mock(McpRuntimeRefreshBroadcaster.class);
        McpRuntimeRefreshCoordinator coordinator = new McpRuntimeRefreshCoordinator(clientManager, broadcaster);

        coordinator.refreshAll("startup_or_manual");

        InOrder inOrder = inOrder(clientManager, broadcaster);
        inOrder.verify(clientManager).reloadEnabledServers();
        inOrder.verify(broadcaster).publishAllRefresh("startup_or_manual");
    }

    @Test
    void applyRemoteServerRefreshOnlyAppliesLocalManagerCall() {
        DynamicMcpClientManager clientManager = mock(DynamicMcpClientManager.class);
        McpRuntimeRefreshBroadcaster broadcaster = mock(McpRuntimeRefreshBroadcaster.class);
        McpRuntimeRefreshCoordinator coordinator = new McpRuntimeRefreshCoordinator(clientManager, broadcaster);
        McpRuntimeRefreshMessage message = McpRuntimeRefreshMessage.serverRefresh("remote-instance", 9L,
                "tool_enable");

        coordinator.applyRemote(message);

        InOrder inOrder = inOrder(clientManager);
        inOrder.verify(clientManager).refreshServer(9L);
        verifyNoInteractions(broadcaster);
    }

    @Test
    void applyRemoteServerDisableOnlyAppliesLocalManagerCall() {
        DynamicMcpClientManager clientManager = mock(DynamicMcpClientManager.class);
        McpRuntimeRefreshBroadcaster broadcaster = mock(McpRuntimeRefreshBroadcaster.class);
        McpRuntimeRefreshCoordinator coordinator = new McpRuntimeRefreshCoordinator(clientManager, broadcaster);
        McpRuntimeRefreshMessage message = McpRuntimeRefreshMessage.serverDisable("remote-instance", 9L,
                "server_disable");

        coordinator.applyRemote(message);

        InOrder inOrder = inOrder(clientManager);
        inOrder.verify(clientManager).disableServer(9L);
        verifyNoInteractions(broadcaster);
    }

    @Test
    void applyRemoteAllRefreshOnlyAppliesLocalManagerCall() {
        DynamicMcpClientManager clientManager = mock(DynamicMcpClientManager.class);
        McpRuntimeRefreshBroadcaster broadcaster = mock(McpRuntimeRefreshBroadcaster.class);
        McpRuntimeRefreshCoordinator coordinator = new McpRuntimeRefreshCoordinator(clientManager, broadcaster);
        McpRuntimeRefreshMessage message = McpRuntimeRefreshMessage.allRefresh("remote-instance",
                "startup_or_manual");

        coordinator.applyRemote(message);

        InOrder inOrder = inOrder(clientManager);
        inOrder.verify(clientManager).reloadEnabledServers();
        verifyNoInteractions(broadcaster);
    }

}
