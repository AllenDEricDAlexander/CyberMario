package top.egon.mario.clocktower.game.mic.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.clocktower.game.mic.po.ClocktowerGamePublicMicSessionPo;

import java.util.Optional;

public interface ClocktowerGamePublicMicSessionRepository
        extends JpaRepository<ClocktowerGamePublicMicSessionPo, Long> {

    Optional<ClocktowerGamePublicMicSessionPo> findByGameIdAndDayNoAndDeletedFalse(Long gameId, int dayNo);

    Optional<ClocktowerGamePublicMicSessionPo> findTopByGameIdAndDeletedFalseOrderByDayNoDescIdDesc(Long gameId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select session
            from ClocktowerGamePublicMicSessionPo session
            where session.gameId = :gameId
              and session.dayNo = :dayNo
              and session.deleted = false
            """)
    Optional<ClocktowerGamePublicMicSessionPo> findLockedByGameIdAndDayNo(
            @Param("gameId") Long gameId, @Param("dayNo") int dayNo);
}
