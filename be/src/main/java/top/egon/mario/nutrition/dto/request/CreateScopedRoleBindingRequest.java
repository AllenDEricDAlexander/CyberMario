package top.egon.mario.nutrition.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import top.egon.mario.nutrition.po.enums.NutritionRoleCode;
import top.egon.mario.nutrition.po.enums.NutritionScopeType;
import top.egon.mario.nutrition.po.enums.NutritionSubjectType;

/**
 * Request body for creating or replacing a scoped nutrition role binding.
 */
public record CreateScopedRoleBindingRequest(
        @NotNull NutritionSubjectType subjectType,
        @NotNull @Min(1) Long subjectId,
        @NotNull NutritionRoleCode roleCode,
        @NotNull NutritionScopeType scopeType,
        @NotNull @Min(1) Long scopeId
) {
}
