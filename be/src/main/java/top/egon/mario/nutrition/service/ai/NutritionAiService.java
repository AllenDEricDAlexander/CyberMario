package top.egon.mario.nutrition.service.ai;

import jakarta.validation.constraints.NotNull;
import top.egon.mario.nutrition.dto.response.NutritionAiRecommendationJobResponse;
import top.egon.mario.nutrition.dto.response.NutritionAiRecommendationResponse;
import top.egon.mario.nutrition.po.enums.NutritionMealType;

import java.time.LocalDate;
import java.util.List;

/**
 * Application service for AI nutrition recommendation jobs.
 */
public interface NutritionAiService {

    NutritionAiRecommendationJobResponse generateManualRecommendation(@NotNull Long familyId,
                                                                       @NotNull LocalDate plannedDate,
                                                                       List<NutritionMealType> targetMealTypes,
                                                                       Long actorId);

    NutritionAiRecommendationJobResponse generateScheduledRecommendation(@NotNull Long familyId,
                                                                         @NotNull LocalDate plannedDate);

    int runPendingJobs(int limit);

    NutritionAiRecommendationJobResponse getJob(@NotNull Long familyId, @NotNull Long jobId, Long actorId);

    List<NutritionAiRecommendationResponse> listRecommendations(@NotNull Long familyId, Long actorId);

    NutritionAiRecommendationResponse getRecommendation(@NotNull Long familyId, @NotNull Long recommendationId,
                                                        Long actorId);
}
