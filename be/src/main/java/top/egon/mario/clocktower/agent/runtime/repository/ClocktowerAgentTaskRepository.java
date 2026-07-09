package top.egon.mario.clocktower.agent.runtime.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.clocktower.agent.runtime.po.ClocktowerAgentTaskPo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ClocktowerAgentTaskRepository extends JpaRepository<ClocktowerAgentTaskPo, Long> {

    Optional<ClocktowerAgentTaskPo> findByIdAndDeletedFalse(Long id);

    Optional<ClocktowerAgentTaskPo> findByGameIdAndAgentInstanceIdAndTriggerTypeAndTriggerKeyAndDeletedFalse(
            Long gameId, Long agentInstanceId, String triggerType, String triggerKey);

    Optional<ClocktowerAgentTaskPo> findByGameIdAndTriggerTypeAndAgentInstanceIdAndDeletedFalse(
            Long gameId, String triggerType, Long agentInstanceId);

    List<ClocktowerAgentTaskPo> findByGameIdAndTriggerTypeAndDeletedFalseOrderByIdAsc(
            Long gameId, String triggerType);

    List<ClocktowerAgentTaskPo> findByGameIdAndDeletedFalseOrderByIdAsc(Long gameId);

    List<ClocktowerAgentTaskPo> findByGameIdAndAgentInstanceIdAndDeletedFalseOrderByIdDesc(
            Long gameId, Long agentInstanceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select task
            from ClocktowerAgentTaskPo task
            where task.status = :status
              and task.availableAt <= :availableAt
              and task.deleted = false
            order by task.priority asc, task.availableAt asc, task.id asc
            """)
    List<ClocktowerAgentTaskPo> claimPendingForWorker(@Param("status") String status,
                                                      @Param("availableAt") Instant availableAt,
                                                      Pageable pageable);

    @Query(value = """
            select *
            from clocktower_agent_task
            where status = 'PENDING'
              and available_at <= :availableAt
              and deleted = false
            order by priority asc, available_at asc, id asc
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<ClocktowerAgentTaskPo> claimPendingForWorkerPostgreSql(@Param("availableAt") Instant availableAt,
                                                                @Param("limit") int limit);
}
