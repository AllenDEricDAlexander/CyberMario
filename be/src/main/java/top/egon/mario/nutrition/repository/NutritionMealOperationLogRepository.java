package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionMealOperationLogPo;

public interface NutritionMealOperationLogRepository extends JpaRepository<NutritionMealOperationLogPo, Long> {
}
