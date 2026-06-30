package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionBudgetRulePo;

public interface NutritionBudgetRuleRepository extends JpaRepository<NutritionBudgetRulePo, Long> {
}
