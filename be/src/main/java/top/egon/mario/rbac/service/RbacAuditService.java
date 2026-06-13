package top.egon.mario.rbac.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.rbac.po.AuditLogPo;
import top.egon.mario.rbac.repository.AuditLogRepository;

import java.time.Instant;

/**
 * Persists RBAC audit events for sensitive changes and authentication flows.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RbacAuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void log(Long actorUserId, String action, String targetType, Long targetId,
                    String beforeJson, String afterJson, String ip, String userAgent) {
        AuditLogPo auditLog = new AuditLogPo();
        auditLog.setActorUserId(actorUserId);
        auditLog.setAction(action);
        auditLog.setTargetType(targetType);
        auditLog.setTargetId(targetId);
        auditLog.setBeforeJson(beforeJson);
        auditLog.setAfterJson(afterJson);
        auditLog.setIp(ip);
        auditLog.setUserAgent(userAgent);
        auditLog.setCreatedAt(Instant.now());
        auditLogRepository.save(auditLog);
        if (log.isDebugEnabled()) {
            log.debug("rbac audit log saved, action={}, targetType={}, targetId={}, actorUserId={}",
                    action, targetType, targetId, actorUserId);
        }
    }

}
