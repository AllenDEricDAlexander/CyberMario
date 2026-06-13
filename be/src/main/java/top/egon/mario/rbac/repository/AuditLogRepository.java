package top.egon.mario.rbac.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.rbac.po.AuditLogPo;

/**
 * Repository for RBAC audit logs.
 */
public interface AuditLogRepository extends JpaRepository<AuditLogPo, Long> {
}
