package top.egon.mario.clocktower.board.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.clocktower.board.po.ClocktowerBoardRolePo;

import java.util.List;

public interface ClocktowerBoardRoleRepository extends JpaRepository<ClocktowerBoardRolePo, Long> {

    List<ClocktowerBoardRolePo> findByBoardConfigIdAndDeletedFalseOrderBySortOrderAsc(Long boardConfigId);
}
