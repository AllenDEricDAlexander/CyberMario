package top.egon.mario.agent.memory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.agent.memory.po.AgentMemoryExtractionAuditPo;

import java.util.List;

/**
 * Repository for long-term memory extraction audit records.
 */
public interface AgentMemoryExtractionAuditRepository extends JpaRepository<AgentMemoryExtractionAuditPo, Long> {

    List<AgentMemoryExtractionAuditPo> findByUserIdOrderByCreatedAtDesc(Long userId);

}
