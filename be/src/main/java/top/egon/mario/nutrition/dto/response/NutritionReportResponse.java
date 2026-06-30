package top.egon.mario.nutrition.dto.response;

import top.egon.mario.nutrition.po.enums.NutritionRiskLevel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Basic family nutrition report snapshot for weekly and monthly periods.
 */
public record NutritionReportResponse(
        Long snapshotId,
        String periodType,
        LocalDate periodStart,
        LocalDate periodEnd,
        NutritionNutrientsResponse totalNutrients,
        Map<NutritionRiskLevel, Long> riskCounts,
        BigDecimal totalCost,
        BigDecimal actualCost,
        BigDecimal estimatedCost,
        List<CommonDish> commonDishes
) {

    public record CommonDish(
            String dishName,
            long count
    ) {
    }
}
