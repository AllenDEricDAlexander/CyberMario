package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionRecipePo;
import top.egon.mario.nutrition.po.enums.NutritionStatus;

import java.util.List;
import java.util.Optional;

public interface NutritionRecipeRepository extends JpaRepository<NutritionRecipePo, Long> {

    List<NutritionRecipePo> findByFamilyIdAndStatusAndDeletedFalseOrderByIdDesc(
            Long familyId, NutritionStatus status);

    Optional<NutritionRecipePo> findByIdAndStatusAndDeletedFalse(Long id, NutritionStatus status);
}
