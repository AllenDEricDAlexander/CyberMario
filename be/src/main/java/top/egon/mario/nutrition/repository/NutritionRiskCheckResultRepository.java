package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionRiskCheckResultPo;

public interface NutritionRiskCheckResultRepository extends JpaRepository<NutritionRiskCheckResultPo, Long> {
}
