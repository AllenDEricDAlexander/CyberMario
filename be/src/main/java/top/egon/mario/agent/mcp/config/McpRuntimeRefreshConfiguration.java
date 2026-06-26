package top.egon.mario.agent.mcp.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import top.egon.mario.agent.mcp.runtime.McpRuntimeRefreshProperties;
import top.egon.mario.agent.mcp.runtime.McpRuntimeRefreshSubscriber;

/**
 * Enables Redis Pub/Sub refresh for dynamic MCP runtime changes.
 */
@Configuration
@EnableConfigurationProperties(McpRuntimeRefreshProperties.class)
@ConditionalOnProperty(prefix = "agent.mcp.runtime", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpRuntimeRefreshConfiguration {

    /**
     * Registers Redis Pub/Sub listener for cross-node MCP runtime refresh.
     */
    @Bean
    @ConditionalOnProperty(prefix = "agent.mcp.runtime.refresh", name = "broadcast-enabled", havingValue = "true",
            matchIfMissing = true)
    public RedisMessageListenerContainer mcpRuntimeRefreshMessageListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            McpRuntimeRefreshProperties refreshProperties,
            McpRuntimeRefreshSubscriber subscriber
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(refreshProperties.broadcastTopic()));
        return container;
    }

}
