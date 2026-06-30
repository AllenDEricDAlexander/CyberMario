package top.egon.mario.nutrition.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.nutrition.po.NutritionMealPlanPo;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NutritionMealPlanRepository extends JpaRepository<NutritionMealPlanPo, Long> {

    Optional<NutritionMealPlanPo> findByIdAndFamilyIdAndDeletedFalse(Long id, Long familyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select mealPlan from NutritionMealPlanPo mealPlan "
            + "where mealPlan.id = :id and mealPlan.familyId = :familyId and mealPlan.deleted = false")
    Optional<NutritionMealPlanPo> findLockedByIdAndFamilyIdAndDeletedFalse(
            @Param("id") Long id, @Param("familyId") Long familyId);

    Optional<NutritionMealPlanPo> findFirstByAiRecommendationIdAndDeletedFalseOrderByIdDesc(
            Long aiRecommendationId);

    List<NutritionMealPlanPo> findByAiRecommendationIdInAndDeletedFalse(Collection<Long> aiRecommendationIds);

    List<NutritionMealPlanPo> findByFamilyIdAndDeletedFalseOrderByPlanDateDescIdDesc(Long familyId);

    List<NutritionMealPlanPo> findByFamilyIdAndPlanDateAndDeletedFalseOrderByIdDesc(
            Long familyId, LocalDate planDate);
}
