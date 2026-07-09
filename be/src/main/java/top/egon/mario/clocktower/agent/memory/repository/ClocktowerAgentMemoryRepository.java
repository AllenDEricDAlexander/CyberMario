package top.egon.mario.clocktower.agent.memory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.clocktower.agent.memory.po.ClocktowerAgentMemoryPo;

import java.util.Collection;
import java.util.List;

public interface ClocktowerAgentMemoryRepository extends JpaRepository<ClocktowerAgentMemoryPo, Long> {

    List<ClocktowerAgentMemoryPo> findByGameIdAndAgentInstanceIdAndDeletedFalseOrderByCreatedAtAscIdAsc(
            Long gameId, Long agentInstanceId);

    List<ClocktowerAgentMemoryPo> findByGameIdAndAgentInstanceIdAndDeletedFalseOrderByCreatedAtDescIdDesc(
            Long gameId, Long agentInstanceId);

    boolean existsByGameIdAndAgentInstanceIdAndSourceEventIdAndMemoryTypeAndSubjectGameSeatIdAndDeletedFalse(
            Long gameId, Long agentInstanceId, Long sourceEventId, String memoryType, Long subjectGameSeatId);

    @Query("""
            select memory
            from ClocktowerAgentMemoryPo memory
            where memory.gameId = :gameId
              and memory.agentInstanceId = :agentInstanceId
              and memory.sourceEventId = :sourceEventId
              and memory.memoryType = :memoryType
              and memory.deleted = false
              and memory.subjectGameSeatId is null
            """)
    List<ClocktowerAgentMemoryPo> findNullSubjectEventMemory(
            @Param("gameId") Long gameId,
            @Param("agentInstanceId") Long agentInstanceId,
            @Param("sourceEventId") Long sourceEventId,
            @Param("memoryType") String memoryType);

    List<ClocktowerAgentMemoryPo> findByGameIdAndAgentInstanceIdAndMemoryTypeInAndDeletedFalseOrderByCreatedAtAscIdAsc(
            Long gameId, Long agentInstanceId, Collection<String> memoryTypes);
}
