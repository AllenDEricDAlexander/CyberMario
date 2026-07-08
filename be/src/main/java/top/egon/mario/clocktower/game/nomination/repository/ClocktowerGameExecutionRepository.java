package top.egon.mario.clocktower.game.nomination.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.clocktower.game.nomination.po.ClocktowerGameExecutionPo;

import java.util.Optional;

public interface ClocktowerGameExecutionRepository extends JpaRepository<ClocktowerGameExecutionPo, Long> {

    Optional<ClocktowerGameExecutionPo> findByGameIdAndDayNoAndDeletedFalse(Long gameId, int dayNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select execution
            from ClocktowerGameExecutionPo execution
            where execution.gameId = :gameId
              and execution.dayNo = :dayNo
              and execution.deleted = false
            """)
    Optional<ClocktowerGameExecutionPo> findLockedByGameIdAndDayNo(
            @Param("gameId") Long gameId, @Param("dayNo") int dayNo);
}
