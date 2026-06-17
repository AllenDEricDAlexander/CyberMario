package top.egon.mario.clocktower.grimoire.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.clocktower.grimoire.po.ClocktowerStatusMarkerPo;

import java.util.List;
import java.util.Optional;

public interface ClocktowerStatusMarkerRepository extends JpaRepository<ClocktowerStatusMarkerPo, Long> {

    List<ClocktowerStatusMarkerPo> findByRoomIdAndDeletedFalseOrderByIdAsc(Long roomId);

    List<ClocktowerStatusMarkerPo> findByRoomIdAndActiveTrueAndDeletedFalseOrderByIdAsc(Long roomId);

    Optional<ClocktowerStatusMarkerPo> findByIdAndRoomIdAndDeletedFalse(Long id, Long roomId);
}
