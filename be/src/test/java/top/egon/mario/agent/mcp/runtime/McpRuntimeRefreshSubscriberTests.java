package top.egon.mario.agent.mcp.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifies Redis Pub/Sub messages are filtered before remote MCP runtime refresh is applied.
 */
class McpRuntimeRefreshSubscriberTests {

    @Test
    void appliesRemoteServerRefreshMessage() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        McpRuntimeRefreshCoordinator coordinator = mock(McpRuntimeRefreshCoordinator.class);
        McpRuntimeInstanceIdentity instanceIdentity = mock(McpRuntimeInstanceIdentity.class);
        McpRuntimeRefreshSubscriber subscriber = new McpRuntimeRefreshSubscriber(objectMapper, coordinator,
                instanceIdentity);
        McpRuntimeRefreshMessage refreshMessage = McpRuntimeRefreshMessage.serverRefresh("remote-instance", 9L,
                "tool_enable");

        subscriber.onMessage(message(objectMapper.writeValueAsBytes(refreshMessage)), null);

        verify(coordinator).applyRemote(refreshMessage);
    }

    @Test
    void appliesRemoteServerDisableMessage() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        McpRuntimeRefreshCoordinator coordinator = mock(McpRuntimeRefreshCoordinator.class);
        McpRuntimeInstanceIdentity instanceIdentity = mock(McpRuntimeInstanceIdentity.class);
        McpRuntimeRefreshSubscriber subscriber = new McpRuntimeRefreshSubscriber(objectMapper, coordinator,
                instanceIdentity);
        McpRuntimeRefreshMessage refreshMessage = McpRuntimeRefreshMessage.serverDisable("remote-instance", 9L,
                "server_disable");

        subscriber.onMessage(message(objectMapper.writeValueAsBytes(refreshMessage)), null);

        verify(coordinator).applyRemote(refreshMessage);
    }

    @Test
    void appliesRemoteAllRefreshMessage() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        McpRuntimeRefreshCoordinator coordinator = mock(McpRuntimeRefreshCoordinator.class);
        McpRuntimeInstanceIdentity instanceIdentity = mock(McpRuntimeInstanceIdentity.class);
        McpRuntimeRefreshSubscriber subscriber = new McpRuntimeRefreshSubscriber(objectMapper, coordinator,
                instanceIdentity);
        McpRuntimeRefreshMessage refreshMessage = McpRuntimeRefreshMessage.allRefresh("remote-instance",
                "startup_or_manual");

        subscriber.onMessage(message(objectMapper.writeValueAsBytes(refreshMessage)), null);

        verify(coordinator).applyRemote(refreshMessage);
    }

    @Test
    void skipsMessagesPublishedByCurrentInstance() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        McpRuntimeRefreshCoordinator coordinator = mock(McpRuntimeRefreshCoordinator.class);
        McpRuntimeInstanceIdentity instanceIdentity = mock(McpRuntimeInstanceIdentity.class);
        when(instanceIdentity.isLocalSource("current-instance")).thenReturn(true);
        McpRuntimeRefreshSubscriber subscriber = new McpRuntimeRefreshSubscriber(objectMapper, coordinator,
                instanceIdentity);
        McpRuntimeRefreshMessage refreshMessage = McpRuntimeRefreshMessage.serverRefresh("current-instance", 9L,
                "tool_enable");

        subscriber.onMessage(message(objectMapper.writeValueAsBytes(refreshMessage)), null);

        verifyNoInteractions(coordinator);
    }

    @Test
    void skipsServerMessagesWithoutServerId() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        McpRuntimeRefreshCoordinator coordinator = mock(McpRuntimeRefreshCoordinator.class);
        McpRuntimeInstanceIdentity instanceIdentity = mock(McpRuntimeInstanceIdentity.class);
        McpRuntimeRefreshSubscriber subscriber = new McpRuntimeRefreshSubscriber(objectMapper, coordinator,
                instanceIdentity);
        McpRuntimeRefreshMessage refreshMessage = new McpRuntimeRefreshMessage("remote-instance",
                McpRuntimeRefreshMessage.EventType.SERVER_REFRESH, null, "tool_enable", null);

        subscriber.onMessage(message(objectMapper.writeValueAsBytes(refreshMessage)), null);

        verify(coordinator, never()).applyRemote(refreshMessage);
    }

    @Test
    void invalidJsonDoesNotEscapeAndDoesNotInteractWithCoordinator() {
        McpRuntimeRefreshCoordinator coordinator = mock(McpRuntimeRefreshCoordinator.class);
        McpRuntimeRefreshSubscriber subscriber = new McpRuntimeRefreshSubscriber(
                new ObjectMapper().findAndRegisterModules(), coordinator, mock(McpRuntimeInstanceIdentity.class));

        subscriber.onMessage(message("{invalid".getBytes()), null);

        verifyNoInteractions(coordinator);
    }

    private static Message message(byte[] body) {
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(body);
        return message;
    }

}
