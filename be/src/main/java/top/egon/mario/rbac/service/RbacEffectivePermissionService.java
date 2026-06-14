package top.egon.mario.rbac.service;

import jakarta.validation.constraints.NotNull;
import top.egon.mario.rbac.dto.response.EffectivePermissionResponse;
import top.egon.mario.rbac.dto.response.MenuTreeResponse;

import java.util.List;
import java.util.Set;

/**
 * Calculates effective roles, permissions and menus after RBAC1 inheritance expansion.
 */
public interface RbacEffectivePermissionService {

    EffectivePermissionResponse getUserEffectivePermissions(@NotNull Long userId);

    List<MenuTreeResponse> getUserMenuTree(@NotNull Long userId);

    Set<Long> resolveEffectiveRoleIds(@NotNull Long userId);

    Set<String> getUserApiAuthorities(@NotNull Long userId);

}
