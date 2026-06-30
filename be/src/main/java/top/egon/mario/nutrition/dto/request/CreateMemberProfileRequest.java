package top.egon.mario.nutrition.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import top.egon.mario.nutrition.po.enums.NutritionMemberType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for creating a family member profile.
 */
public record CreateMemberProfileRequest(
        Long boundUserId,
        @NotBlank @Size(min = 1, max = 128) String nickname,
        @Size(max = 32) String gender,
        LocalDate birthDate,
        @DecimalMin("0.0") BigDecimal heightCm,
        @DecimalMin("0.0") BigDecimal weightKg,
        @NotNull NutritionMemberType memberType,
        Boolean loginEnabled,
        Long guardianMemberId
) {
}
