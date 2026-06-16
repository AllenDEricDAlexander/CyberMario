package top.egon.mario.agent.memory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    Optional<AgentMemorySessionPo> findBySessionIdAndDeletedFalse(String sessionId);

    List<AgentMemorySessionPo> findByUserIdAndEntryTypeAndStatusAndDeletedFalseOrderByUpdatedAtDesc(
            Long userId, AgentMemoryEntryType entryType, AgentMemorySessionStatus status);

    Page<AgentMemorySessionPo> findByUserIdAndStatusInAndDeletedFalse(Long userId,
                                                                      List<AgentMemorySessionStatus> statuses,
                                                                      Pageable pageable);

    Page<AgentMemorySessionPo> findByUserIdAndEntryTypeAndStatusInAndDeletedFalse(Long userId,
                                                                                  AgentMemoryEntryType entryType,
                                                                                  List<AgentMemorySessionStatus> statuses,
                                                                                  Pageable pageable);

}
