package top.egon.mario.nutrition.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * Family daily nutrition totals with member-level breakdowns.
 */
public record NutritionDailyOverviewResponse(
        Long familyId,
        LocalDate recordDate,
        NutritionNutrientsResponse totalNutrients,
        List<MemberSummary> memberSummaries
) {

    public record MemberSummary(
            Long memberProfileId,
            NutritionNutrientsResponse totalNutrients,
            List<NutritionRecordResponse> records
    ) {
    }
}
