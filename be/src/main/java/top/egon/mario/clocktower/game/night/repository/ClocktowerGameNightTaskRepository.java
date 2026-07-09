package top.egon.mario.clocktower.game.night.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.clocktower.game.night.po.ClocktowerGameNightTaskPo;

import java.util.Collection;
import java.util.List;

public interface ClocktowerGameNightTaskRepository extends JpaRepository<ClocktowerGameNightTaskPo, Long> {

    List<ClocktowerGameNightTaskPo> findByGameIdAndNightNoAndDeletedFalseOrderBySortOrderAscIdAsc(
            Long gameId, int nightNo);

    long countByGameIdAndNightNoAndMandatoryTrueAndDeletedFalse(Long gameId, int nightNo);

    long countByGameIdAndNightNoAndMandatoryTrueAndStatusInAndDeletedFalse(
            Long gameId, int nightNo, Collection<String> statuses);

    @Query("""
            select count(task)
            from ClocktowerGameNightTaskPo task
            where task.gameId = :gameId
              and task.nightNo = :nightNo
              and task.mandatory = true
              and task.status not in :completedStatuses
              and task.deleted = false
            """)
    long countPendingMandatoryTasks(
            @Param("gameId") Long gameId,
            @Param("nightNo") int nightNo,
            @Param("completedStatuses") Collection<String> completedStatuses);
}
