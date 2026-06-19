package top.egon.mario.clocktower.board.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import top.egon.mario.clocktower.board.po.ClocktowerBoardConfigPo;

import java.util.List;
import java.util.Optional;

public interface ClocktowerBoardConfigRepository extends JpaRepository<ClocktowerBoardConfigPo, Long>,
        JpaSpecificationExecutor<ClocktowerBoardConfigPo> {

    Optional<ClocktowerBoardConfigPo> findByIdAndDeletedFalse(Long id);

    List<ClocktowerBoardConfigPo> findByDeletedFalseOrderByIdDesc();

    Optional<ClocktowerBoardConfigPo> findByBoardCodeAndDeletedFalse(String boardCode);
}
