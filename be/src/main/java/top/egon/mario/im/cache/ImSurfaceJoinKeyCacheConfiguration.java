package top.egon.mario.im.cache;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables configuration for the IM surface join-key cache.
 */
@Configuration
@EnableConfigurationProperties(ImSurfaceJoinKeyCacheProperties.class)
public class ImSurfaceJoinKeyCacheConfiguration {
}
