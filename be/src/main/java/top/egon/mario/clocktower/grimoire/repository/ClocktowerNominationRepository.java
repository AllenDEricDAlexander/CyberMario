package top.egon.mario.clocktower.grimoire.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.clocktower.grimoire.po.ClocktowerNominationPo;

import java.util.List;
import java.util.Optional;

public interface ClocktowerNominationRepository extends JpaRepository<ClocktowerNominationPo, Long> {

    List<ClocktowerNominationPo> findByRoomIdAndDeletedFalseOrderByIdAsc(Long roomId);

    Optional<ClocktowerNominationPo> findTopByRoomIdAndStatusAndDeletedFalseOrderByIdDesc(Long roomId, String status);
}
