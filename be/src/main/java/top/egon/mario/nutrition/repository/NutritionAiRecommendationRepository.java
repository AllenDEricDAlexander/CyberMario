package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionAiRecommendationPo;

public interface NutritionAiRecommendationRepository extends JpaRepository<NutritionAiRecommendationPo, Long> {
}
