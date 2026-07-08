package top.egon.mario.clocktower.agent.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentInstancePo;

import java.util.List;
import java.util.Optional;

public interface ClocktowerAgentInstanceRepository extends JpaRepository<ClocktowerAgentInstancePo, Long> {

    List<ClocktowerAgentInstancePo> findByRoomIdAndDeletedFalseOrderByIdAsc(Long roomId);

    List<ClocktowerAgentInstancePo> findByGameIdAndDeletedFalseOrderByIdAsc(Long gameId);

    Optional<ClocktowerAgentInstancePo> findByIdAndDeletedFalse(Long id);

    Optional<ClocktowerAgentInstancePo> findByGameSeatIdAndDeletedFalse(Long gameSeatId);

    Optional<ClocktowerAgentInstancePo> findByActorIdAndDeletedFalse(Long actorId);
}
