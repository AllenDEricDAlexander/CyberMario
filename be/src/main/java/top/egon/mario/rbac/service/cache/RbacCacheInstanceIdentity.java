package top.egon.mario.rbac.service.cache;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Identifies the current application instance in RBAC cache eviction broadcasts.
 */
@Component
public class RbacCacheInstanceIdentity {

    private final String sourceInstanceId = UUID.randomUUID().toString();

    public String sourceInstanceId() {
        return sourceInstanceId;
    }

    public boolean isLocalSource(String sourceInstanceId) {
        return this.sourceInstanceId.equals(sourceInstanceId);
    }

}
