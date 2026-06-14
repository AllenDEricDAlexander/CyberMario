package top.egon.mario.rbac.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import top.egon.mario.rbac.dto.request.PermissionRequest;
import top.egon.mario.rbac.dto.response.MenuTreeResponse;
import top.egon.mario.rbac.dto.response.PermissionResponse;
import top.egon.mario.rbac.po.PermissionPo;
import top.egon.mario.rbac.service.model.ApiPermissionRule;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Permission management service for MENU, BUTTON and API resources.
 */
public interface RbacPermissionService {

    PermissionResponse createPermission(@Valid @NotNull PermissionRequest request, Long actorUserId);

    PermissionPo getPermissionPo(@NotNull Long permissionId);

    PermissionResponse getPermission(@NotNull Long permissionId);

    Page<PermissionResponse> getPermissionPage(@NotNull Pageable pageable);

    Page<PermissionResponse> getApiPermissionPage(@NotNull Pageable pageable);

    List<MenuTreeResponse> getMenuTree();

    List<PermissionResponse> getButtonsByMenuId(@NotNull Long menuPermissionId);

    Set<Long> getButtonApiIds(@NotNull Long buttonPermissionId);

    PermissionResponse updatePermission(@NotNull Long permissionId, @Valid @NotNull PermissionRequest request, Long actorUserId);

    void updateStatus(@NotNull Long permissionId, @NotNull top.egon.mario.rbac.dto.enums.PermissionStatus status);

    void deletePermission(@NotNull Long permissionId);

    void replaceButtonApis(@NotNull Long buttonPermissionId, Collection<Long> apiPermissionIds, Long actorUserId);

    List<ApiPermissionRule> findEnabledApiRules();

}
