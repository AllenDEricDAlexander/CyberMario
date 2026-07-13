package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionRecipeStepPo;

import java.util.Collection;
import java.util.List;

public interface NutritionRecipeStepRepository extends JpaRepository<NutritionRecipeStepPo, Long> {

    List<NutritionRecipeStepPo> findByRecipeIdAndDeletedFalseOrderByStepNoAscIdAsc(Long recipeId);

    List<NutritionRecipeStepPo> findByRecipeIdInAndDeletedFalseOrderByStepNoAscIdAsc(Collection<Long> recipeIds);
}
