package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionAiRecommendationPo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface NutritionAiRecommendationRepository extends JpaRepository<NutritionAiRecommendationPo, Long> {

    List<NutritionAiRecommendationPo> findByFamilyIdAndDeletedFalseOrderByRecommendationDateDescIdDesc(
            Long familyId);

    Optional<NutritionAiRecommendationPo> findByIdAndFamilyIdAndDeletedFalse(Long id, Long familyId);

    Optional<NutritionAiRecommendationPo> findFirstByAiJobIdAndDeletedFalseOrderByIdDesc(Long aiJobId);

    boolean existsByFamilyIdAndRecommendationDateAndDeletedFalse(Long familyId, LocalDate recommendationDate);
}
