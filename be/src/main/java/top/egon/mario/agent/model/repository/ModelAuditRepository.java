package top.egon.mario.agent.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.agent.model.po.ModelAuditPo;

/**
 * Repository for model call audit records.
 */
public interface ModelAuditRepository extends JpaRepository<ModelAuditPo, Long> {
}
