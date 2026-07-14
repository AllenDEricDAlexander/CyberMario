package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionBudgetRulePo;
import top.egon.mario.nutrition.po.enums.NutritionStatus;

import java.util.List;
import java.util.Optional;

public interface NutritionBudgetRuleRepository extends JpaRepository<NutritionBudgetRulePo, Long> {

    List<NutritionBudgetRulePo> findByFamilyIdAndEnabledTrueAndStatusAndDeletedFalseOrderByIdAsc(
            Long familyId, NutritionStatus status);

    List<NutritionBudgetRulePo> findByFamilyIdAndDeletedFalseOrderByIdAsc(Long familyId);

    Optional<NutritionBudgetRulePo> findByIdAndFamilyIdAndDeletedFalse(Long id, Long familyId);
}
