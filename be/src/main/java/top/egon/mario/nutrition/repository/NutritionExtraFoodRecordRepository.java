package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionExtraFoodRecordPo;

public interface NutritionExtraFoodRecordRepository extends JpaRepository<NutritionExtraFoodRecordPo, Long> {
}
