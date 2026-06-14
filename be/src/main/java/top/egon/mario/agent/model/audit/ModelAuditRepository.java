package top.egon.mario.agent.model.audit;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for model call audit records.
 */
public interface ModelAuditRepository extends JpaRepository<ModelAuditPo, Long> {
}
