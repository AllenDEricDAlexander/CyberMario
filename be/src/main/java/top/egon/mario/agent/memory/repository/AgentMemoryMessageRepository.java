package top.egon.mario.agent.memory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.agent.memory.po.AgentMemoryMessagePo;

import java.util.List;

/**
 * Repository for normalized memory messages.
 */
public interface AgentMemoryMessageRepository extends JpaRepository<AgentMemoryMessagePo, Long> {

    List<AgentMemoryMessagePo> findBySessionIdAndDeletedFalseOrderBySeqNoAsc(String sessionId);

    List<AgentMemoryMessagePo> findTop40BySessionIdAndDeletedFalseOrderBySeqNoDesc(String sessionId);

}
