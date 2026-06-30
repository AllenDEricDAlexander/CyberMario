package top.egon.mario.nutrition.dto.response;

import top.egon.mario.nutrition.po.enums.NutritionImportStatus;
import top.egon.mario.nutrition.po.enums.NutritionImportType;

import java.time.Instant;
import java.util.List;

/**
 * Import job response DTO.
 */
public record NutritionImportJobResponse(
        Long id,
        Long familyId,
        NutritionImportType importType,
        String fileName,
        NutritionImportStatus status,
        int totalRows,
        int successRows,
        int failedRows,
        int warningRows,
        String errorSummary,
        List<NutritionImportErrorResponse> errors,
        Instant createdAt,
        Instant completedAt,
        Instant confirmedAt
) {
}
