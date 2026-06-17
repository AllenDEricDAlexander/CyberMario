package top.egon.mario.clocktower.grimoire.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.clocktower.grimoire.po.ClocktowerStorytellerTaskPo;

import java.util.List;

public interface ClocktowerStorytellerTaskRepository extends JpaRepository<ClocktowerStorytellerTaskPo, Long> {

    List<ClocktowerStorytellerTaskPo> findByRoomIdAndDeletedFalseOrderBySortOrderAsc(Long roomId);

    List<ClocktowerStorytellerTaskPo> findByRoomIdAndStatusAndDeletedFalseOrderBySortOrderAsc(Long roomId, String status);
}
