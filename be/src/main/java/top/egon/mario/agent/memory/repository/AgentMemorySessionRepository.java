package top.egon.mario.agent.memory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import top.egon.mario.agent.memory.po.AgentMemorySessionPo;
import top.egon.mario.agent.memory.po.enums.AgentMemoryEntryType;
import top.egon.mario.agent.memory.po.enums.AgentMemorySessionStatus;

import java.util.List;
import java.util.Optional;

/**
 * Repository for user-owned memory sessions.
 */
public interface AgentMemorySessionRepository extends JpaRepository<AgentMemorySessionPo, Long>,
        JpaSpecificationExecutor<AgentMemorySessionPo> {

    Optional<AgentMemorySessionPo> findBySessionIdAndUserIdAndDeletedFalse(String sessionId, Long userId);

    List<AgentMemorySessionPo> findByUserIdAndEntryTypeAndStatusAndDeletedFalseOrderByUpdatedAtDesc(
            Long userId, AgentMemoryEntryType entryType, AgentMemorySessionStatus status);

}
