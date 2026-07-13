package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionBudgetRulePo;
import top.egon.mario.nutrition.po.enums.NutritionStatus;

import java.util.List;

public interface NutritionBudgetRuleRepository extends JpaRepository<NutritionBudgetRulePo, Long> {

    List<NutritionBudgetRulePo> findByFamilyIdAndEnabledTrueAndStatusAndDeletedFalseOrderByIdAsc(
            Long familyId, NutritionStatus status);
}
