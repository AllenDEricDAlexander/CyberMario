package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionRiskCheckResultPo;
import top.egon.mario.nutrition.po.enums.NutritionStatus;

import java.util.Collection;
import java.util.List;

public interface NutritionRiskCheckResultRepository extends JpaRepository<NutritionRiskCheckResultPo, Long> {

    List<NutritionRiskCheckResultPo> findByFamilyIdAndStatusAndResolvedFalseAndDeletedFalseOrderByIdAsc(
            Long familyId, NutritionStatus status);

    List<NutritionRiskCheckResultPo> findByFamilyIdAndSourceTypeAndSourceIdAndStatusAndResolvedFalseAndDeletedFalseOrderByIdAsc(
            Long familyId, String sourceType, Long sourceId, NutritionStatus status);

    List<NutritionRiskCheckResultPo> findByFamilyIdAndMemberProfileIdAndSourceTypeAndSourceIdAndStatusAndResolvedFalseAndDeletedFalseOrderByIdAsc(
            Long familyId, Long memberProfileId, String sourceType, Long sourceId, NutritionStatus status);

    List<NutritionRiskCheckResultPo> findByFamilyIdAndSourceTypeAndSourceIdInAndStatusAndResolvedFalseAndDeletedFalseOrderByIdAsc(
            Long familyId, String sourceType, Collection<Long> sourceIds, NutritionStatus status);
}
