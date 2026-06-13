package top.egon.mario.rbac.service.cache;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Enables RBAC cache configuration properties.
 */
@Configuration
@EnableConfigurationProperties(RbacCacheProperties.class)
public class RbacCacheConfiguration {

    /**
     * Registers Redis Pub/Sub listener for cross-node RBAC local cache invalidation.
     */
    @Bean
    public RedisMessageListenerContainer rbacCacheEvictionMessageListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            RbacCacheProperties cacheProperties,
            RbacCacheEvictionSubscriber subscriber
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        if (cacheProperties.enabled() && cacheProperties.broadcastEnabled()) {
            container.addMessageListener(subscriber, new ChannelTopic(cacheProperties.broadcastTopic()));
        }
        return container;
    }

}
