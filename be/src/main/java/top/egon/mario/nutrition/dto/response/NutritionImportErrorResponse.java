package top.egon.mario.nutrition.dto.response;

/**
 * Import row error or warning response DTO.
 */
public record NutritionImportErrorResponse(
        Long id,
        int rowNo,
        String columnName,
        String errorCode,
        String errorMessage,
        String severity
) {
}
