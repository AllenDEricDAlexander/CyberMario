package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionRecipeIngredientPo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NutritionRecipeIngredientRepository extends JpaRepository<NutritionRecipeIngredientPo, Long> {

    Optional<NutritionRecipeIngredientPo> findByIdAndDeletedFalse(Long id);

    List<NutritionRecipeIngredientPo> findByRecipeIdAndDeletedFalseOrderByIdAsc(Long recipeId);

    List<NutritionRecipeIngredientPo> findByRecipeIdInAndDeletedFalseOrderByIdAsc(Collection<Long> recipeIds);
}
