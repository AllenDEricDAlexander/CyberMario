package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionRecipePo;

public interface NutritionRecipeRepository extends JpaRepository<NutritionRecipePo, Long> {
}
