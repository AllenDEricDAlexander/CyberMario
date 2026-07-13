package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionMealOperationLogPo;

import java.util.List;

public interface NutritionMealOperationLogRepository extends JpaRepository<NutritionMealOperationLogPo, Long> {

    List<NutritionMealOperationLogPo> findByFamilyIdAndMealPlanIdAndDeletedFalseOrderByOperatedAtAscIdAsc(
            Long familyId, Long mealPlanId);
}
