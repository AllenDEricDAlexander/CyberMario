package top.egon.mario.rbac.dto;

import lombok.Builder;

import java.util.Set;

/**
 * Effective role and permission summary after RBAC1 inheritance expansion.
 */
@Builder
public record EffectivePermissionResponse(
        Set<Long> roleIds,
        Set<String> roleCodes,
        Set<String> menuCodes,
        Set<String> buttonCodes,
        Set<String> apiCodes
) {
}
