package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionMealPlanItemPo;
import top.egon.mario.nutrition.po.enums.NutritionStatus;

import java.util.Collection;
import java.util.List;

public interface NutritionMealPlanItemRepository extends JpaRepository<NutritionMealPlanItemPo, Long> {

    List<NutritionMealPlanItemPo> findByMealPlanIdAndStatusAndDeletedFalseOrderBySortOrderAscIdAsc(
            Long mealPlanId, NutritionStatus status);

    List<NutritionMealPlanItemPo> findByMealPlanIdInAndStatusAndDeletedFalseOrderBySortOrderAscIdAsc(
            Collection<Long> mealPlanIds, NutritionStatus status);
}
