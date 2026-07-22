package top.egon.mario.agent.memory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.agent.memory.po.AgentLongTermMemoryPo;
import top.egon.mario.agent.memory.po.enums.AgentLongTermMemoryScopeType;

import java.util.Optional;

/**
 * Repository for current long-term memory documents.
 */
public interface AgentLongTermMemoryRepository extends JpaRepository<AgentLongTermMemoryPo, Long> {

    Optional<AgentLongTermMemoryPo> findByUserIdAndScopeTypeAndScopeKeyAndDeletedFalse(
            Long userId, AgentLongTermMemoryScopeType scopeType, String scopeKey);

}
