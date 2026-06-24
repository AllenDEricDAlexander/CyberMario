package top.egon.mario.clocktower.room.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.clocktower.game.po.ClocktowerRoomSeatPo;

import java.util.List;
import java.util.Optional;

public interface ClocktowerRoomSeatRepository extends JpaRepository<ClocktowerRoomSeatPo, Long> {

    @Query("""
            select seat
            from ClocktowerRoomSeatPo seat
            where seat.roomId = :roomId
              and seat.deleted = false
            order by seat.seatNo asc
            """)
    List<ClocktowerRoomSeatPo> findByRoomIdOrderBySeatNoAsc(@Param("roomId") Long roomId);

    List<ClocktowerRoomSeatPo> findByRoomIdAndDeletedFalseOrderBySeatNoAsc(Long roomId);

    @Query("""
            select seat
            from ClocktowerRoomSeatPo seat
            where seat.roomId = :roomId
              and seat.seatNo = :seatNo
              and seat.deleted = false
            """)
    Optional<ClocktowerRoomSeatPo> findByRoomIdAndSeatNo(@Param("roomId") Long roomId,
                                                         @Param("seatNo") int seatNo);

    Optional<ClocktowerRoomSeatPo> findByRoomIdAndSeatNoAndDeletedFalse(Long roomId, int seatNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select seat
            from ClocktowerRoomSeatPo seat
            where seat.roomId = :roomId
              and seat.seatNo = :seatNo
              and seat.deleted = false
            """)
    Optional<ClocktowerRoomSeatPo> findLockedByRoomIdAndSeatNo(@Param("roomId") Long roomId,
                                                               @Param("seatNo") int seatNo);

    @Query("""
            select seat
            from ClocktowerRoomSeatPo seat
            where seat.roomId = :roomId
              and seat.userId = :userId
              and seat.deleted = false
            """)
    Optional<ClocktowerRoomSeatPo> findByRoomIdAndUserId(@Param("roomId") Long roomId,
                                                         @Param("userId") Long userId);

    Optional<ClocktowerRoomSeatPo> findByRoomIdAndUserIdAndDeletedFalse(Long roomId, Long userId);
}
