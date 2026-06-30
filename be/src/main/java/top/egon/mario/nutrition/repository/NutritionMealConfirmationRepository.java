package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionMealConfirmationPo;
import top.egon.mario.nutrition.po.enums.NutritionConfirmationStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NutritionMealConfirmationRepository extends JpaRepository<NutritionMealConfirmationPo, Long> {

    Optional<NutritionMealConfirmationPo> findByIdAndFamilyIdAndDeletedFalse(Long id, Long familyId);

    Optional<NutritionMealConfirmationPo> findByMealPlanIdAndMemberProfileIdAndDeletedFalse(
            Long mealPlanId, Long memberProfileId);

    List<NutritionMealConfirmationPo> findByMealPlanIdAndConfirmationStatusAndDeletedFalse(
            Long mealPlanId, NutritionConfirmationStatus confirmationStatus);

    List<NutritionMealConfirmationPo> findByMealPlanIdInAndConfirmationStatusAndDeletedFalse(
            Collection<Long> mealPlanIds, NutritionConfirmationStatus confirmationStatus);

    long countByMealPlanIdAndConfirmationStatusAndDeletedFalse(
            Long mealPlanId, NutritionConfirmationStatus confirmationStatus);
}
