package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionMealConfirmationItemPo;

public interface NutritionMealConfirmationItemRepository extends JpaRepository<NutritionMealConfirmationItemPo, Long> {
}
