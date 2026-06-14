package top.egon.mario.rbac.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import top.egon.mario.rbac.dto.request.CreateRoleRequest;
import top.egon.mario.rbac.dto.request.UpdateRoleRequest;
import top.egon.mario.rbac.dto.response.RoleResponse;
import top.egon.mario.rbac.po.RolePo;

import java.util.Collection;
import java.util.Set;

/**
 * Role management service for metadata, inheritance and permission grants.
 */
public interface RbacRoleService {

    RoleResponse createRole(@Valid @NotNull CreateRoleRequest request);

    RolePo getRolePo(@NotNull Long roleId);

    RoleResponse getRole(@NotNull Long roleId);

    Page<RoleResponse> getRolePage(@NotNull Pageable pageable);

    RoleResponse updateRole(@NotNull Long roleId, @Valid @NotNull UpdateRoleRequest request);

    void deleteRole(@NotNull Long roleId);

    void replaceRoleInheritance(@NotNull Long roleId, Collection<Long> inheritedRoleIds, Long actorUserId);

    Set<Long> replaceRolePermissions(@NotNull Long roleId, Collection<Long> permissionIds, boolean syncButtonApis,
                                     Long actorUserId);

    Set<Long> getEffectivePermissionIds(@NotNull Long roleId);

    Set<Long> getDirectPermissionIds(@NotNull Long roleId);

    Set<Long> getInheritedRoleIds(@NotNull Long roleId);

}
