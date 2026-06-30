package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionFoodPriceRecordPo;

public interface NutritionFoodPriceRecordRepository extends JpaRepository<NutritionFoodPriceRecordPo, Long> {
}
