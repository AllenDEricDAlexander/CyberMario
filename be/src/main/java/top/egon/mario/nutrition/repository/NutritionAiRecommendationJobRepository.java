package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionAiRecommendationJobPo;
import top.egon.mario.nutrition.po.enums.NutritionAiJobStatus;
import top.egon.mario.nutrition.po.enums.NutritionAiTriggerType;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;

public interface NutritionAiRecommendationJobRepository extends JpaRepository<NutritionAiRecommendationJobPo, Long> {

    Optional<NutritionAiRecommendationJobPo> findFirstByFamilyIdAndTriggerTypeAndPlannedDateAndStatusInAndDeletedFalseOrderByIdDesc(
            Long familyId, NutritionAiTriggerType triggerType, LocalDate plannedDate,
            Collection<NutritionAiJobStatus> statuses);
}
