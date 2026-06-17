package top.egon.mario.clocktower.grimoire.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.clocktower.grimoire.po.ClocktowerGrimoireEntryPo;

import java.util.List;
import java.util.Optional;

public interface ClocktowerGrimoireEntryRepository extends JpaRepository<ClocktowerGrimoireEntryPo, Long> {

    List<ClocktowerGrimoireEntryPo> findByRoomIdAndDeletedFalseOrderBySeatIdAsc(Long roomId);

    Optional<ClocktowerGrimoireEntryPo> findByRoomIdAndSeatIdAndDeletedFalse(Long roomId, Long seatId);
}
