package top.egon.mario.nutrition.dto.request;

import jakarta.validation.constraints.NotNull;
import top.egon.mario.nutrition.po.enums.NutritionGrantPermissionLevel;

import java.time.Instant;

/**
 * Request body for changing the permission and expiration of a data grant.
 */
public record UpdateDataGrantRequest(
        @NotNull NutritionGrantPermissionLevel permissionLevel,
        Instant expiresAt
) {
}
