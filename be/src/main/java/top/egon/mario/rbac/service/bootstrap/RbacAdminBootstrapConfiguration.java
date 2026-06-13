package top.egon.mario.rbac.service.bootstrap;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables RBAC administrator bootstrap configuration properties.
 */
@Configuration
@EnableConfigurationProperties(RbacAdminBootstrapProperties.class)
public class RbacAdminBootstrapConfiguration {
}
