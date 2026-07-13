package top.egon.mario.nutrition.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.nutrition.po.NutritionMealConfirmationPo;
import top.egon.mario.nutrition.po.enums.NutritionConfirmationStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NutritionMealConfirmationRepository extends JpaRepository<NutritionMealConfirmationPo, Long> {

    Optional<NutritionMealConfirmationPo> findByIdAndFamilyIdAndDeletedFalse(Long id, Long familyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select confirmation from NutritionMealConfirmationPo confirmation "
            + "where confirmation.id = :id and confirmation.familyId = :familyId and confirmation.deleted = false")
    Optional<NutritionMealConfirmationPo> findLockedByIdAndFamilyIdAndDeletedFalse(
            @Param("id") Long id, @Param("familyId") Long familyId);

    Optional<NutritionMealConfirmationPo> findByMealPlanIdAndMemberProfileIdAndDeletedFalse(
            Long mealPlanId, Long memberProfileId);

    List<NutritionMealConfirmationPo> findByMealPlanIdAndDeletedFalseOrderByIdAsc(Long mealPlanId);

    List<NutritionMealConfirmationPo> findByMealPlanIdAndConfirmationStatusAndDeletedFalse(
            Long mealPlanId, NutritionConfirmationStatus confirmationStatus);

    List<NutritionMealConfirmationPo> findByMealPlanIdInAndConfirmationStatusAndDeletedFalse(
            Collection<Long> mealPlanIds, NutritionConfirmationStatus confirmationStatus);

    long countByMealPlanIdAndConfirmationStatusAndDeletedFalse(
            Long mealPlanId, NutritionConfirmationStatus confirmationStatus);
}
