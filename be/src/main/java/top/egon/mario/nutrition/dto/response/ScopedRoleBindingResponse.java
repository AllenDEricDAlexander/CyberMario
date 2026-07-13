package top.egon.mario.nutrition.dto.response;

import top.egon.mario.nutrition.po.enums.NutritionRoleCode;
import top.egon.mario.nutrition.po.enums.NutritionScopeType;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.po.enums.NutritionSubjectType;

import java.time.Instant;

/**
 * Family-scoped nutrition role binding response DTO.
 */
public record ScopedRoleBindingResponse(
        Long id,
        NutritionSubjectType subjectType,
        Long subjectId,
        NutritionRoleCode roleCode,
        NutritionScopeType scopeType,
        Long scopeId,
        NutritionStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
