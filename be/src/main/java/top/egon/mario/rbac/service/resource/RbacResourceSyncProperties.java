package top.egon.mario.rbac.service.resource;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for startup RBAC resource synchronization.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "mario.rbac.resource-sync")
public class RbacResourceSyncProperties {

    private boolean enabled = true;

}
