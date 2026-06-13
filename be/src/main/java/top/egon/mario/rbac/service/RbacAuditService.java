package top.egon.mario.rbac.service;

/**
 * Persists RBAC audit events for sensitive changes and authentication flows.
 */
public interface RbacAuditService {

    void log(Long actorUserId, String action, String targetType, Long targetId,
             String beforeJson, String afterJson, String ip, String userAgent);

}
