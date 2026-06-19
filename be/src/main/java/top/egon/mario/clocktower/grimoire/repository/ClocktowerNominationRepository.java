package top.egon.mario.clocktower.grimoire.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.clocktower.grimoire.po.ClocktowerNominationPo;

import java.util.List;
import java.util.Optional;

public interface ClocktowerNominationRepository extends JpaRepository<ClocktowerNominationPo, Long> {

    List<ClocktowerNominationPo> findByRoomIdAndDeletedFalseOrderByIdAsc(Long roomId);

    List<ClocktowerNominationPo> findByRoomIdAndDayNoAndDeletedFalseOrderByIdAsc(Long roomId, int dayNo);

    List<ClocktowerNominationPo> findByRoomIdAndDayNoAndStatusAndDeletedFalseOrderByIdAsc(
            Long roomId, int dayNo, String status);

    Optional<ClocktowerNominationPo> findByIdAndRoomIdAndDeletedFalse(Long id, Long roomId);

    Optional<ClocktowerNominationPo> findTopByRoomIdAndStatusAndDeletedFalseOrderByIdDesc(Long roomId, String status);
}
