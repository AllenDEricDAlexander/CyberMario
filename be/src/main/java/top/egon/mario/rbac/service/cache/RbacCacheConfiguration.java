package top.egon.mario.rbac.service.cache;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables RBAC cache configuration properties.
 */
@Configuration
@EnableConfigurationProperties(RbacCacheProperties.class)
public class RbacCacheConfiguration {
}
