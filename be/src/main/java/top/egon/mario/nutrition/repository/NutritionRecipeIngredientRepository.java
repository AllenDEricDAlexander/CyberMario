package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionRecipeIngredientPo;

import java.util.Collection;
import java.util.List;

public interface NutritionRecipeIngredientRepository extends JpaRepository<NutritionRecipeIngredientPo, Long> {

    List<NutritionRecipeIngredientPo> findByRecipeIdAndDeletedFalseOrderByIdAsc(Long recipeId);

    List<NutritionRecipeIngredientPo> findByRecipeIdInAndDeletedFalseOrderByIdAsc(Collection<Long> recipeIds);
}
