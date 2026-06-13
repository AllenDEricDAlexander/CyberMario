package top.egon.mario.rbac.service.cache;

import java.util.Collection;
import java.util.List;

/**
 * Redis Pub/Sub payload for cross-node RBAC local cache invalidation.
 */
public record RbacCacheEvictionMessage(
        String sourceInstanceId,
        Scope scope,
        List<Long> userIds,
        String reason
) {

    public RbacCacheEvictionMessage {
        sourceInstanceId = sourceInstanceId == null ? "" : sourceInstanceId;
        userIds = userIds == null ? List.of() : List.copyOf(userIds);
        reason = reason == null ? "" : reason;
    }

    public static RbacCacheEvictionMessage allPermissions(String sourceInstanceId, String reason) {
        return new RbacCacheEvictionMessage(sourceInstanceId, Scope.PERMISSION_ALL, List.of(), reason);
    }

    public static RbacCacheEvictionMessage userPermissions(String sourceInstanceId, Collection<Long> userIds, String reason) {
        return new RbacCacheEvictionMessage(sourceInstanceId, Scope.PERMISSION_USERS,
                userIds == null ? List.of() : List.copyOf(userIds), reason);
    }

    public enum Scope {
        PERMISSION_ALL,
        PERMISSION_USERS
    }

}
