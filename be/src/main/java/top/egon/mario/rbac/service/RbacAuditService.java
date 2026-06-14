package top.egon.mario.rbac.service;

import jakarta.validation.constraints.NotBlank;

/**
 * Persists RBAC audit events for sensitive changes and authentication flows.
 */
public interface RbacAuditService {

    void log(Long actorUserId, @NotBlank String action, @NotBlank String targetType, Long targetId,
             String beforeJson, String afterJson, String ip, String userAgent);

}
