package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionMealConfirmationPo;

public interface NutritionMealConfirmationRepository extends JpaRepository<NutritionMealConfirmationPo, Long> {
}
