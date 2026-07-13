package top.egon.mario.nutrition.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import top.egon.mario.nutrition.po.enums.NutritionGrantDataScope;
import top.egon.mario.nutrition.po.enums.NutritionGrantPermissionLevel;

import java.time.Instant;

/**
 * Request body for granting family-scoped nutrition data access.
 */
public record CreateDataGrantRequest(
        @Min(1) Long memberProfileId,
        @NotNull @Pattern(regexp = "USER|CLAN") String granteeType,
        @NotNull @Min(1) Long granteeId,
        @NotNull NutritionGrantDataScope dataScope,
        @NotNull NutritionGrantPermissionLevel permissionLevel,
        Instant expiresAt
) {
}
