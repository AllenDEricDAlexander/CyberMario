package top.egon.mario.clocktower.agent.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentInstancePo;

import java.util.List;
import java.util.Optional;

public interface ClocktowerAgentInstanceRepository extends JpaRepository<ClocktowerAgentInstancePo, Long> {

    List<ClocktowerAgentInstancePo> findByRoomIdAndDeletedFalseOrderByIdAsc(Long roomId);

    List<ClocktowerAgentInstancePo> findByGameIdAndDeletedFalseOrderByIdAsc(Long gameId);

    Optional<ClocktowerAgentInstancePo> findByIdAndDeletedFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select instance
            from ClocktowerAgentInstancePo instance
            where instance.id = :id
              and instance.deleted = false
            """)
    Optional<ClocktowerAgentInstancePo> findLockedByIdAndDeletedFalse(@Param("id") Long id);

    Optional<ClocktowerAgentInstancePo> findByGameSeatIdAndDeletedFalse(Long gameSeatId);

    Optional<ClocktowerAgentInstancePo> findByActorIdAndDeletedFalse(Long actorId);
}
