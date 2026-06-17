package top.egon.mario.clocktower.room.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.clocktower.room.po.ClocktowerSeatPo;

import java.util.List;
import java.util.Optional;

public interface ClocktowerSeatRepository extends JpaRepository<ClocktowerSeatPo, Long> {

    List<ClocktowerSeatPo> findByRoomIdAndDeletedFalseOrderBySeatNoAsc(Long roomId);

    Optional<ClocktowerSeatPo> findByIdAndRoomIdAndDeletedFalse(Long id, Long roomId);

    Optional<ClocktowerSeatPo> findByRoomIdAndSeatNoAndDeletedFalse(Long roomId, int seatNo);

    Optional<ClocktowerSeatPo> findByRoomIdAndUserIdAndDeletedFalse(Long roomId, Long userId);
}
