package top.egon.mario.clocktower.script.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.script.po.ClocktowerNightOrderPo;

import java.util.Collection;
import java.util.List;

public interface ClocktowerNightOrderRepository extends JpaRepository<ClocktowerNightOrderPo, Long> {

    List<ClocktowerNightOrderPo> findByScriptCodeAndDeletedFalseOrderBySortOrderAsc(ClocktowerScriptCode scriptCode);

    List<ClocktowerNightOrderPo> findByScriptCodeAndNightTypeAndDeletedFalseOrderBySortOrderAsc(ClocktowerScriptCode scriptCode,
                                                                                                String nightType);

    List<ClocktowerNightOrderPo> findByScriptCodeAndNightTypeAndRoleCodeInAndDeletedFalseOrderBySortOrderAsc(ClocktowerScriptCode scriptCode,
                                                                                                             String nightType,
                                                                                                             Collection<String> roleCodes);
}
