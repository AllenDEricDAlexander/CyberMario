package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionMealPlanItemPo;

public interface NutritionMealPlanItemRepository extends JpaRepository<NutritionMealPlanItemPo, Long> {
}
