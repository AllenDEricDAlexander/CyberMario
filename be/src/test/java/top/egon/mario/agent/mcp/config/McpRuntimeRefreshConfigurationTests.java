package top.egon.mario.agent.mcp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.SubscriptionListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import top.egon.mario.agent.mcp.runtime.DynamicMcpClientManager;
import top.egon.mario.agent.mcp.runtime.McpRuntimeInstanceIdentity;
import top.egon.mario.agent.mcp.runtime.McpRuntimeRefreshBroadcaster;
import top.egon.mario.agent.mcp.runtime.McpRuntimeRefreshCoordinator;
import top.egon.mario.agent.mcp.runtime.McpRuntimeRefreshProperties;
import top.egon.mario.agent.mcp.runtime.McpRuntimeRefreshSubscriber;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Verifies MCP runtime Redis refresh beans are controlled by configuration properties.
 */
class McpRuntimeRefreshConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(McpRuntimeRefreshConfiguration.class)
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
            .withBean(RedisConnectionFactory.class, McpRuntimeRefreshConfigurationTests::redisConnectionFactory)
            .withBean(DynamicMcpClientManager.class, () -> mock(DynamicMcpClientManager.class))
            .withBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules());

    @Test
    void createsRefreshBeansWhenRuntimeRefreshIsEnabled() {
        contextRunner
                .withUserConfiguration(ScannedMcpRuntimeRefreshComponents.class)
                .withPropertyValues("agent.mcp.runtime.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(McpRuntimeRefreshProperties.class);
                    assertThat(context).hasSingleBean(McpRuntimeRefreshBroadcaster.class);
                    assertThat(context).hasSingleBean(McpRuntimeRefreshCoordinator.class);
                    assertThat(context).hasSingleBean(McpRuntimeRefreshSubscriber.class);
                    assertThat(context).hasSingleBean(RedisMessageListenerContainer.class);
                    assertThat(context.getBean(McpRuntimeRefreshProperties.class).broadcastTopic())
                            .isEqualTo("agent:mcp:runtime:refresh");
                });
    }

    @Test
    void skipsListenerContainerWhenRuntimeRefreshBroadcastIsDisabled() {
        contextRunner
                .withUserConfiguration(ScannedMcpRuntimeRefreshComponents.class)
                .withPropertyValues(
                        "agent.mcp.runtime.enabled=true",
                        "agent.mcp.runtime.refresh.broadcast-enabled=false"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(McpRuntimeRefreshProperties.class);
                    assertThat(context).hasSingleBean(McpRuntimeRefreshBroadcaster.class);
                    assertThat(context).hasSingleBean(McpRuntimeRefreshCoordinator.class);
                    assertThat(context).hasSingleBean(McpRuntimeRefreshSubscriber.class);
                    assertThat(context).doesNotHaveBean(RedisMessageListenerContainer.class);
                });
    }

    @Test
    void skipsRefreshBeansWhenRuntimeRefreshIsDisabled() {
        contextRunner
                .withUserConfiguration(ScannedMcpRuntimeRefreshComponents.class)
                .withPropertyValues("agent.mcp.runtime.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(McpRuntimeRefreshBroadcaster.class);
                    assertThat(context).doesNotHaveBean(McpRuntimeRefreshCoordinator.class);
                    assertThat(context).doesNotHaveBean(McpRuntimeRefreshSubscriber.class);
                    assertThat(context).doesNotHaveBean(RedisMessageListenerContainer.class);
                });
    }

    private static RedisConnectionFactory redisConnectionFactory() {
        RedisConnectionFactory redisConnectionFactory = mock(RedisConnectionFactory.class);
        RedisConnection redisConnection = mock(RedisConnection.class);
        doAnswer(invocation -> {
            MessageListener listener = invocation.getArgument(0);
            byte[] channel = invocation.getArgument(1);
            if (listener instanceof SubscriptionListener subscriptionListener) {
                subscriptionListener.onChannelSubscribed(channel, 1L);
            }
            return null;
        }).when(redisConnection).subscribe(any(MessageListener.class), any(byte[].class));
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        return redisConnectionFactory;
    }

    @Configuration
    @ComponentScan(
            basePackageClasses = McpRuntimeRefreshBroadcaster.class,
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
                    McpRuntimeInstanceIdentity.class,
                    McpRuntimeRefreshBroadcaster.class,
                    McpRuntimeRefreshCoordinator.class,
                    McpRuntimeRefreshSubscriber.class
            })
    )
    static class ScannedMcpRuntimeRefreshComponents {
    }

}
