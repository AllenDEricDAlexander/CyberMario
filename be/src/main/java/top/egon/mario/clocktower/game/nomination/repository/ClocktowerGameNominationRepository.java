package top.egon.mario.clocktower.game.nomination.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.clocktower.game.nomination.po.ClocktowerGameNominationPo;

import java.util.List;
import java.util.Optional;

public interface ClocktowerGameNominationRepository extends JpaRepository<ClocktowerGameNominationPo, Long> {

    Optional<ClocktowerGameNominationPo> findByIdAndGameIdAndDeletedFalse(Long id, Long gameId);

    Optional<ClocktowerGameNominationPo> findTopByGameIdAndStatusAndDeletedFalseOrderByIdDesc(
            Long gameId, String status);

    boolean existsByGameIdAndDayNoAndNominatorGameSeatIdAndDeletedFalse(Long gameId, int dayNo, Long seatId);

    boolean existsByGameIdAndDayNoAndNomineeGameSeatIdAndDeletedFalse(Long gameId, int dayNo, Long seatId);

    List<ClocktowerGameNominationPo> findByGameIdAndDayNoAndStatusAndDeletedFalseOrderByIdAsc(
            Long gameId, int dayNo, String status);

    List<ClocktowerGameNominationPo> findByGameIdAndDayNoAndDeletedFalseOrderByIdAsc(Long gameId, int dayNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select nomination
            from ClocktowerGameNominationPo nomination
            where nomination.id = :id
              and nomination.gameId = :gameId
              and nomination.deleted = false
            """)
    Optional<ClocktowerGameNominationPo> findLockedByIdAndGameIdAndDeletedFalse(
            @Param("id") Long id, @Param("gameId") Long gameId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select nomination
            from ClocktowerGameNominationPo nomination
            where nomination.gameId = :gameId
              and nomination.status = :status
              and nomination.deleted = false
            order by nomination.id desc
            """)
    List<ClocktowerGameNominationPo> findLockedByGameIdAndStatus(
            @Param("gameId") Long gameId, @Param("status") String status);
}
