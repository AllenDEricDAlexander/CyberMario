package top.egon.mario.clocktower.game.mic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.clocktower.game.mic.po.ClocktowerGamePublicMicTurnPo;

import java.util.List;
import java.util.Optional;

public interface ClocktowerGamePublicMicTurnRepository
        extends JpaRepository<ClocktowerGamePublicMicTurnPo, Long> {

    List<ClocktowerGamePublicMicTurnPo> findBySessionIdAndDeletedFalseOrderByTurnOrderAscIdAsc(Long sessionId);

    Optional<ClocktowerGamePublicMicTurnPo> findByIdAndDeletedFalse(Long id);

    Optional<ClocktowerGamePublicMicTurnPo> findFirstBySessionIdAndStatusAndDeletedFalseOrderByStartedAtDescIdDesc(
            Long sessionId, String status);

    Optional<ClocktowerGamePublicMicTurnPo> findFirstBySessionIdAndStatusAndDeletedFalseOrderByTurnOrderAscIdAsc(
            Long sessionId, String status);
}
