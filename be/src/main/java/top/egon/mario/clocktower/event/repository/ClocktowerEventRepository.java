package top.egon.mario.clocktower.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.clocktower.event.po.ClocktowerEventPo;

import java.util.List;
import java.util.Optional;

public interface ClocktowerEventRepository extends JpaRepository<ClocktowerEventPo, Long> {

    List<ClocktowerEventPo> findByRoomIdAndDeletedFalseOrderByEventSeqAsc(Long roomId);

    List<ClocktowerEventPo> findByRoomIdAndEventSeqGreaterThanAndDeletedFalseOrderByEventSeqAsc(Long roomId, Long eventSeq);

    Optional<ClocktowerEventPo> findTopByRoomIdAndDeletedFalseOrderByEventSeqDesc(Long roomId);
}
