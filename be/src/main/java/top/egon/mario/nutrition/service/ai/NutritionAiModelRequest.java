package top.egon.mario.nutrition.service.ai;

import top.egon.mario.nutrition.po.enums.NutritionMealType;

import java.time.LocalDate;
import java.util.List;

/**
 * Input passed to the AI model boundary for one nutrition recommendation job.
 */
public record NutritionAiModelRequest(
        Long jobId,
        Long familyId,
        String familyName,
        LocalDate plannedDate,
        List<NutritionMealType> mealTypes,
        String inputSnapshot,
        Long requestedBy
) {

    public NutritionAiModelRequest {
        mealTypes = mealTypes == null ? List.of() : List.copyOf(mealTypes);
    }
}
