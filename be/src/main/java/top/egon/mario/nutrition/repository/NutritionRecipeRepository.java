package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionRecipePo;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.po.enums.NutritionRecipeSourceType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NutritionRecipeRepository extends JpaRepository<NutritionRecipePo, Long> {

    List<NutritionRecipePo> findByFamilyIdAndStatusAndDeletedFalseOrderByIdDesc(
            Long familyId, NutritionStatus status);

    List<NutritionRecipePo> findByFamilyIdIsNullAndSourceTypeAndStatusAndDeletedFalseOrderByIdDesc(
            NutritionRecipeSourceType sourceType, NutritionStatus status);

    Optional<NutritionRecipePo> findByIdAndFamilyIdIsNullAndSourceTypeAndDeletedFalse(
            Long id, NutritionRecipeSourceType sourceType);

    Optional<NutritionRecipePo> findByIdAndStatusAndDeletedFalse(Long id, NutritionStatus status);

    List<NutritionRecipePo> findByIdInAndStatusAndDeletedFalse(Collection<Long> ids, NutritionStatus status);
}
