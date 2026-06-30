package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionStandardFoodPo;

public interface NutritionStandardFoodRepository extends JpaRepository<NutritionStandardFoodPo, Long> {
}
