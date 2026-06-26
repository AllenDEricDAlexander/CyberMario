package top.egon.mario.agent.mcp.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Verifies Redis Pub/Sub messages for MCP runtime refresh.
 */
class McpRuntimeRefreshBroadcasterTests {

    @Test
    void publishesServerRefreshMessageWhenBroadcastIsEnabled() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        McpRuntimeInstanceIdentity instanceIdentity = new McpRuntimeInstanceIdentity();
        McpRuntimeRefreshBroadcaster broadcaster = new McpRuntimeRefreshBroadcaster(redisTemplate,
                objectMapper, new McpRuntimeRefreshProperties(true, "agent:mcp:runtime:refresh"), instanceIdentity);
        var payloadCaptor = forClass(String.class);

        broadcaster.publishServerRefresh(9L, "tool_enable");

        verify(redisTemplate).convertAndSend(eq("agent:mcp:runtime:refresh"), payloadCaptor.capture());
        McpRuntimeRefreshMessage message = objectMapper.readValue(payloadCaptor.getValue(),
                McpRuntimeRefreshMessage.class);
        assertThat(message.sourceInstanceId()).isEqualTo(instanceIdentity.sourceInstanceId());
        assertThat(message.eventType()).isEqualTo(McpRuntimeRefreshMessage.EventType.SERVER_REFRESH);
        assertThat(message.serverId()).isEqualTo(9L);
        assertThat(message.reason()).isEqualTo("tool_enable");
        assertThat(message.createdAt()).isNotNull();
    }

    @Test
    void publishesServerDisableMessageWhenBroadcastIsEnabled() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        McpRuntimeRefreshBroadcaster broadcaster = new McpRuntimeRefreshBroadcaster(redisTemplate,
                objectMapper, new McpRuntimeRefreshProperties(true, "agent:mcp:runtime:refresh"),
                new McpRuntimeInstanceIdentity());
        var payloadCaptor = forClass(String.class);

        broadcaster.publishServerDisable(9L, "server_disable");

        verify(redisTemplate).convertAndSend(eq("agent:mcp:runtime:refresh"), payloadCaptor.capture());
        McpRuntimeRefreshMessage message = objectMapper.readValue(payloadCaptor.getValue(),
                McpRuntimeRefreshMessage.class);
        assertThat(message.eventType()).isEqualTo(McpRuntimeRefreshMessage.EventType.SERVER_DISABLE);
        assertThat(message.serverId()).isEqualTo(9L);
        assertThat(message.reason()).isEqualTo("server_disable");
    }

    @Test
    void publishesAllRefreshMessageWhenBroadcastIsEnabled() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        McpRuntimeRefreshBroadcaster broadcaster = new McpRuntimeRefreshBroadcaster(redisTemplate,
                objectMapper, new McpRuntimeRefreshProperties(true, "agent:mcp:runtime:refresh"),
                new McpRuntimeInstanceIdentity());
        var payloadCaptor = forClass(String.class);

        broadcaster.publishAllRefresh("startup_or_manual");

        verify(redisTemplate).convertAndSend(eq("agent:mcp:runtime:refresh"), payloadCaptor.capture());
        McpRuntimeRefreshMessage message = objectMapper.readValue(payloadCaptor.getValue(),
                McpRuntimeRefreshMessage.class);
        assertThat(message.eventType()).isEqualTo(McpRuntimeRefreshMessage.EventType.ALL_REFRESH);
        assertThat(message.serverId()).isNull();
        assertThat(message.reason()).isEqualTo("startup_or_manual");
    }

    @Test
    void skipsPublishingWhenBroadcastIsDisabled() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        McpRuntimeRefreshBroadcaster broadcaster = new McpRuntimeRefreshBroadcaster(redisTemplate,
                new ObjectMapper().findAndRegisterModules(),
                new McpRuntimeRefreshProperties(false, "agent:mcp:runtime:refresh"),
                new McpRuntimeInstanceIdentity());

        broadcaster.publishServerRefresh(9L, "tool_enable");

        verify(redisTemplate, never()).convertAndSend(eq("agent:mcp:runtime:refresh"), anyString());
    }

    @Test
    void publishFailureDoesNotEscape() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        doThrow(new RuntimeException("redis down")).when(redisTemplate).convertAndSend(anyString(), anyString());
        McpRuntimeRefreshBroadcaster broadcaster = new McpRuntimeRefreshBroadcaster(redisTemplate,
                new ObjectMapper().findAndRegisterModules(),
                new McpRuntimeRefreshProperties(true, "agent:mcp:runtime:refresh"),
                new McpRuntimeInstanceIdentity());

        broadcaster.publishServerRefresh(9L, "tool_enable");

        verify(redisTemplate).convertAndSend(eq("agent:mcp:runtime:refresh"), anyString());
    }

}
