package top.egon.mario.agent.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import top.egon.mario.agent.po.AgentConversationAuditPo;

/**
 * Repository for agent conversation audit records.
 */
public interface AgentConversationAuditRepository extends JpaRepository<AgentConversationAuditPo, Long>,
        JpaSpecificationExecutor<AgentConversationAuditPo> {
}
