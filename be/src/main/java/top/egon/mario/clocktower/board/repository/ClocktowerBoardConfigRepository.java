package top.egon.mario.clocktower.board.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.clocktower.board.po.ClocktowerBoardConfigPo;

import java.util.List;
import java.util.Optional;

public interface ClocktowerBoardConfigRepository extends JpaRepository<ClocktowerBoardConfigPo, Long> {

    Optional<ClocktowerBoardConfigPo> findByIdAndDeletedFalse(Long id);

    List<ClocktowerBoardConfigPo> findByDeletedFalseOrderByIdDesc();

    Optional<ClocktowerBoardConfigPo> findByBoardCodeAndDeletedFalse(String boardCode);
}
