package top.egon.mario.clocktower.script.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.script.po.ClocktowerRolePo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClocktowerRoleRepository extends JpaRepository<ClocktowerRolePo, Long> {

    List<ClocktowerRolePo> findByScriptCodeAndDeletedFalseOrderBySortOrderAsc(ClocktowerScriptCode scriptCode);

    List<ClocktowerRolePo> findByScriptCodeAndEnabledAndDeletedFalseOrderBySortOrderAsc(ClocktowerScriptCode scriptCode,
                                                                                        boolean enabled);

    List<ClocktowerRolePo> findByScriptCodeAndRoleTypeAndDeletedFalseOrderBySortOrderAsc(ClocktowerScriptCode scriptCode,
                                                                                         ClocktowerRoleType roleType);

    List<ClocktowerRolePo> findByScriptCodeAndRoleTypeAndEnabledAndDeletedFalseOrderBySortOrderAsc(ClocktowerScriptCode scriptCode,
                                                                                                   ClocktowerRoleType roleType,
                                                                                                   boolean enabled);

    List<ClocktowerRolePo> findByRoleCodeInAndDeletedFalse(Collection<String> roleCodes);

    Optional<ClocktowerRolePo> findByRoleCodeAndDeletedFalse(String roleCode);
}
