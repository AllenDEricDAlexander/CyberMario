package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionFoodPriceRecordPo;

import java.util.Optional;
import java.util.List;

public interface NutritionFoodPriceRecordRepository extends JpaRepository<NutritionFoodPriceRecordPo, Long> {

    List<NutritionFoodPriceRecordPo> findTop50ByFamilyIdAndDeletedFalseOrderByPriceDateDescIdDesc(Long familyId);

    List<NutritionFoodPriceRecordPo> findTop50ByFamilyIdAndStandardFoodIdAndDeletedFalseOrderByPriceDateDescIdDesc(
            Long familyId, Long standardFoodId);

    Optional<NutritionFoodPriceRecordPo> findByIdAndFamilyIdAndDeletedFalse(Long id, Long familyId);

    Optional<NutritionFoodPriceRecordPo> findFirstByFamilyIdAndStandardFoodIdAndSpecUnitIgnoreCaseAndDeletedFalseOrderByPriceDateDescIdDesc(
            Long familyId, Long standardFoodId, String specUnit);

    Optional<NutritionFoodPriceRecordPo> findFirstByFamilyIdAndRawFoodNameIgnoreCaseAndSpecUnitIgnoreCaseAndDeletedFalseOrderByPriceDateDescIdDesc(
            Long familyId, String rawFoodName, String specUnit);
}
