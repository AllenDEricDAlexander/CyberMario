package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionRecipeIngredientPo;

public interface NutritionRecipeIngredientRepository extends JpaRepository<NutritionRecipeIngredientPo, Long> {
}
