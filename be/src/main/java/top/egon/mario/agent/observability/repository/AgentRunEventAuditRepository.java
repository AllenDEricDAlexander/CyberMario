package top.egon.mario.agent.observability.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.agent.observability.po.AgentRunEventAuditPo;

import java.util.List;

/**
 * Repository for agent run audit timeline events.
 */
public interface AgentRunEventAuditRepository extends JpaRepository<AgentRunEventAuditPo, Long> {

    List<AgentRunEventAuditPo> findByRunIdOrderBySeqNoAsc(Long runId);
}
