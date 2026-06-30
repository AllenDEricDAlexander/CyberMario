package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionAiRecommendationJobPo;

public interface NutritionAiRecommendationJobRepository extends JpaRepository<NutritionAiRecommendationJobPo, Long> {
}
