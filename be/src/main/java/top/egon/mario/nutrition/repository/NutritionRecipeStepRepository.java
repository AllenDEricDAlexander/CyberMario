package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionRecipeStepPo;

public interface NutritionRecipeStepRepository extends JpaRepository<NutritionRecipeStepPo, Long> {
}
