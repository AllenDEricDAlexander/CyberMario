package top.egon.mario.clocktower.script.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.clocktower.script.po.ClocktowerJinxRulePo;

import java.util.List;

public interface ClocktowerJinxRuleRepository extends JpaRepository<ClocktowerJinxRulePo, Long> {

    List<ClocktowerJinxRulePo> findByDeletedFalseOrderByIdAsc();

    List<ClocktowerJinxRulePo> findBySeverityAndDeletedFalseOrderByIdAsc(String severity);

    @Query("""
            select rule
            from ClocktowerJinxRulePo rule
            where rule.deleted = false
              and (rule.roleACode = :roleCode or rule.roleBCode = :roleCode)
            order by rule.id asc
            """)
    List<ClocktowerJinxRulePo> findByRoleCodeAndDeletedFalseOrderByIdAsc(@Param("roleCode") String roleCode);
}
