package top.egon.mario.clocktower.script.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.script.po.ClocktowerScriptPo;

import java.util.List;
import java.util.Optional;

public interface ClocktowerScriptRepository extends JpaRepository<ClocktowerScriptPo, Long> {

    List<ClocktowerScriptPo> findByEnabledTrueAndDeletedFalseOrderBySortOrderAsc();

    Optional<ClocktowerScriptPo> findByScriptCodeAndDeletedFalse(ClocktowerScriptCode scriptCode);
}
