package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionMealPlanPo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NutritionMealPlanRepository extends JpaRepository<NutritionMealPlanPo, Long> {

    Optional<NutritionMealPlanPo> findFirstByAiRecommendationIdAndDeletedFalseOrderByIdDesc(
            Long aiRecommendationId);

    List<NutritionMealPlanPo> findByAiRecommendationIdInAndDeletedFalse(Collection<Long> aiRecommendationIds);
}
