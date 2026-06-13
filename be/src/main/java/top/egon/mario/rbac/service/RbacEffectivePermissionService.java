package top.egon.mario.rbac.service;

import top.egon.mario.rbac.dto.response.EffectivePermissionResponse;
import top.egon.mario.rbac.dto.response.MenuTreeResponse;

import java.util.List;
import java.util.Set;

/**
 * Calculates effective roles, permissions and menus after RBAC1 inheritance expansion.
 */
public interface RbacEffectivePermissionService {

    EffectivePermissionResponse getUserEffectivePermissions(Long userId);

    List<MenuTreeResponse> getUserMenuTree(Long userId);

    Set<Long> resolveEffectiveRoleIds(Long userId);

    Set<String> getUserApiAuthorities(Long userId);

}
