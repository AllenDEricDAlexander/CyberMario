package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionRecordPo;
import top.egon.mario.nutrition.po.enums.NutritionStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface NutritionRecordRepository extends JpaRepository<NutritionRecordPo, Long> {

    Optional<NutritionRecordPo> findByIdAndFamilyIdAndDeletedFalse(Long id, Long familyId);

    boolean existsByFamilyIdAndMealPlanIdAndSourceTypeAndStatusAndDeletedFalse(
            Long familyId, Long mealPlanId, String sourceType, NutritionStatus status);

    List<NutritionRecordPo> findByFamilyIdAndMealPlanIdAndSourceTypeAndStatusAndDeletedFalseOrderByIdAsc(
            Long familyId, Long mealPlanId, String sourceType, NutritionStatus status);

    List<NutritionRecordPo> findByFamilyIdAndRecordDateAndStatusAndDeletedFalseOrderByMemberProfileIdAscMealTypeAscIdAsc(
            Long familyId, LocalDate recordDate, NutritionStatus status);

    List<NutritionRecordPo> findByFamilyIdAndRecordDateBetweenAndStatusAndDeletedFalseOrderByRecordDateAscIdAsc(
            Long familyId, LocalDate periodStart, LocalDate periodEnd, NutritionStatus status);
}
