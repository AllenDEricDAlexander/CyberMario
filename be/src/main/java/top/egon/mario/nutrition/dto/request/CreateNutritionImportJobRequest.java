package top.egon.mario.nutrition.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import top.egon.mario.nutrition.po.enums.NutritionImportType;

/**
 * Request body for the MVP JSON-backed CSV import flow.
 */
public record CreateNutritionImportJobRequest(
        @NotNull NutritionImportType importType,
        Long familyId,
        @NotBlank @Size(max = 255) String fileName,
        @NotBlank String csvContent
) {
}
