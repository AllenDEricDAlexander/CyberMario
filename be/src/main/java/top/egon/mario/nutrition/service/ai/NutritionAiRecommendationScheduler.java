package top.egon.mario.nutrition.service.ai;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.nutrition.dto.response.NutritionAiRecommendationJobResponse;
import top.egon.mario.nutrition.po.NutritionFamilyPo;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.repository.NutritionFamilyRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Scans due AI-enabled families and creates scheduled recommendation jobs.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NutritionAiRecommendationScheduler {

    private final NutritionFamilyRepository familyRepository;
    private final NutritionAiService aiService;

    public List<NutritionAiRecommendationJobResponse> generateDueRecommendations(LocalDate date, LocalTime now) {
        List<NutritionFamilyPo> dueFamilies = familyRepository
                .findByAiEnabledTrueAndAiGenerateTimeLessThanEqualAndStatusAndDeletedFalse(
                        now, NutritionStatus.ACTIVE);
        List<NutritionAiRecommendationJobResponse> jobs = new ArrayList<>();
        for (NutritionFamilyPo family : dueFamilies) {
            try {
                jobs.add(aiService.generateScheduledRecommendation(family.getId(), date));
            } catch (RuntimeException ex) {
                LogUtil.warn(log).log("nutrition ai recommendation job failed, familyId={}, plannedDate={}, error={}",
                        family.getId(), date, ex.getMessage());
            }
        }
        return jobs;
    }
}
