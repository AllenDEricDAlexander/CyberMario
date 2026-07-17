package top.egon.mario.nutrition.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.nutrition.po.NutritionAiRecommendationJobPo;
import top.egon.mario.nutrition.po.enums.NutritionAiJobStatus;
import top.egon.mario.nutrition.po.enums.NutritionAiTriggerType;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NutritionAiRecommendationJobRepository extends JpaRepository<NutritionAiRecommendationJobPo, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select job from NutritionAiRecommendationJobPo job
            where job.status = :status and job.deleted = false
            order by job.id asc
            """)
    List<NutritionAiRecommendationJobPo> findLockedByStatusOrderByIdAsc(
            @Param("status") NutritionAiJobStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from NutritionAiRecommendationJobPo job where job.id = :id and job.deleted = false")
    Optional<NutritionAiRecommendationJobPo> findLockedByIdAndDeletedFalse(@Param("id") Long id);

    Optional<NutritionAiRecommendationJobPo> findByIdAndFamilyIdAndDeletedFalse(Long id, Long familyId);

    List<NutritionAiRecommendationJobPo> findByStatusAndDeletedFalseOrderByIdAsc(NutritionAiJobStatus status);

    List<NutritionAiRecommendationJobPo> findTop20ByFamilyIdAndDeletedFalseOrderByIdDesc(Long familyId);

    Optional<NutritionAiRecommendationJobPo> findFirstByFamilyIdAndTriggerTypeAndPlannedDateAndStatusInAndDeletedFalseOrderByIdDesc(
            Long familyId, NutritionAiTriggerType triggerType, LocalDate plannedDate,
            Collection<NutritionAiJobStatus> statuses);
}
