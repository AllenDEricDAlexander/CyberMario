package top.egon.mario.clocktower.game.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;

import java.util.List;
import java.util.Optional;

public interface ClocktowerGameSeatRepository extends JpaRepository<ClocktowerGameSeatPo, Long> {

    List<ClocktowerGameSeatPo> findByGameIdAndDeletedFalseOrderBySeatNoAsc(Long gameId);

    List<ClocktowerGameSeatPo> findByGameIdOrderBySeatNoAsc(Long gameId);

    Optional<ClocktowerGameSeatPo> findByGameIdAndRoomSeatId(Long gameId, Long roomSeatId);

    Optional<ClocktowerGameSeatPo> findByGameIdAndRoomSeatIdAndDeletedFalse(Long gameId, Long roomSeatId);
}
