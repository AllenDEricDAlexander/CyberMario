package top.egon.mario.agent.observability.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import top.egon.mario.agent.observability.po.AgentRunAuditPo;

/**
 * Repository for agent run audit summaries.
 */
public interface AgentRunAuditRepository extends JpaRepository<AgentRunAuditPo, Long>,
        JpaSpecificationExecutor<AgentRunAuditPo> {

    @Modifying
    @Query("update AgentRunAuditPo r set r.modelCallCount = r.modelCallCount + 1 where r.id = :runId")
    int incrementModelCallCount(Long runId);

    @Modifying
    @Query("update AgentRunAuditPo r set r.toolCallCount = r.toolCallCount + 1 where r.id = :runId")
    int incrementToolCallCount(Long runId);

    @Modifying
    @Query("update AgentRunAuditPo r set r.mcpToolCallCount = r.mcpToolCallCount + 1 where r.id = :runId")
    int incrementMcpToolCallCount(Long runId);

}
