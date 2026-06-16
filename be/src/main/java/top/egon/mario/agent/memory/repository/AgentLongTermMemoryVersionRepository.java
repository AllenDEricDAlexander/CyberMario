package top.egon.mario.agent.memory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.agent.memory.po.AgentLongTermMemoryVersionPo;

import java.util.List;

/**
 * Repository for long-term memory version snapshots.
 */
public interface AgentLongTermMemoryVersionRepository extends JpaRepository<AgentLongTermMemoryVersionPo, Long> {

    List<AgentLongTermMemoryVersionPo> findByMemoryIdOrderByVersionNoDesc(Long memoryId);

}
