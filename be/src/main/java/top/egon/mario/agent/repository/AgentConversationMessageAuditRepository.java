package top.egon.mario.agent.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.agent.po.AgentConversationMessageAuditPo;

import java.util.List;

/**
 * Repository for agent conversation audit message rows.
 */
public interface AgentConversationMessageAuditRepository extends JpaRepository<AgentConversationMessageAuditPo, Long> {

    List<AgentConversationMessageAuditPo> findByConversationAuditIdOrderBySeqNoAsc(Long conversationAuditId);

}
