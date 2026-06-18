package top.egon.mario.clocktower.ruling.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.clocktower.ruling.po.ClocktowerRulingPo;

import java.util.List;
import java.util.Optional;

public interface ClocktowerRulingRepository extends JpaRepository<ClocktowerRulingPo, Long> {

    List<ClocktowerRulingPo> findByRoomIdAndDeletedFalseOrderByIdDesc(Long roomId);

    Optional<ClocktowerRulingPo> findByIdAndRoomIdAndDeletedFalse(Long id, Long roomId);
}
