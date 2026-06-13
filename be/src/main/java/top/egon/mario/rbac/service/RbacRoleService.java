package top.egon.mario.rbac.service;

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

    RoleResponse createRole(CreateRoleRequest request);

    RolePo getRolePo(Long roleId);

    RoleResponse getRole(Long roleId);

    Page<RoleResponse> getRolePage(Pageable pageable);

    RoleResponse updateRole(Long roleId, UpdateRoleRequest request);

    void deleteRole(Long roleId);

    void replaceRoleInheritance(Long roleId, Collection<Long> inheritedRoleIds, Long actorUserId);

    Set<Long> replaceRolePermissions(Long roleId, Collection<Long> permissionIds, boolean syncButtonApis,
                                     Long actorUserId);

    Set<Long> getEffectivePermissionIds(Long roleId);

    Set<Long> getDirectPermissionIds(Long roleId);

    Set<Long> getInheritedRoleIds(Long roleId);

}
