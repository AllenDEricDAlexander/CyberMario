package top.egon.mario.rbac.service.resource;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables RBAC resource synchronization configuration properties.
 */
@Configuration
@EnableConfigurationProperties(RbacResourceSyncProperties.class)
public class RbacResourceSyncConfiguration {
}
