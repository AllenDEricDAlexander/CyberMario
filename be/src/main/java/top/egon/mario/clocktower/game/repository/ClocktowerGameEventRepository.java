package top.egon.mario.clocktower.game.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.clocktower.game.po.ClocktowerGameEventPo;

import java.util.Optional;

public interface ClocktowerGameEventRepository extends JpaRepository<ClocktowerGameEventPo, Long> {

    Optional<ClocktowerGameEventPo> findTopByGameIdAndDeletedFalseOrderByEventSeqDesc(Long gameId);
}
