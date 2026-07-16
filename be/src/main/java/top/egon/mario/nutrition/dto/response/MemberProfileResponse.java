package top.egon.mario.nutrition.dto.response;

import top.egon.mario.nutrition.po.enums.NutritionMemberType;
import top.egon.mario.nutrition.po.enums.NutritionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Nutrition family member response DTO.
 */
public record MemberProfileResponse(
        Long id,
        Long familyId,
        Long boundUserId,
        String boundUsername,
        boolean ownerProfile,
        String nickname,
        String gender,
        LocalDate birthDate,
        BigDecimal heightCm,
        BigDecimal weightKg,
        NutritionMemberType memberType,
        boolean loginEnabled,
        Long guardianMemberId,
        NutritionStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
