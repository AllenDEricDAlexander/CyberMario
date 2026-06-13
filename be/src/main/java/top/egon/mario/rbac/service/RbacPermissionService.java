package top.egon.mario.rbac.service;

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

    PermissionResponse createPermission(PermissionRequest request, Long actorUserId);

    PermissionPo getPermissionPo(Long permissionId);

    PermissionResponse getPermission(Long permissionId);

    Page<PermissionResponse> getPermissionPage(Pageable pageable);

    Page<PermissionResponse> getApiPermissionPage(Pageable pageable);

    List<MenuTreeResponse> getMenuTree();

    List<PermissionResponse> getButtonsByMenuId(Long menuPermissionId);

    Set<Long> getButtonApiIds(Long buttonPermissionId);

    PermissionResponse updatePermission(Long permissionId, PermissionRequest request, Long actorUserId);

    void updateStatus(Long permissionId, top.egon.mario.rbac.dto.enums.PermissionStatus status);

    void deletePermission(Long permissionId);

    void replaceButtonApis(Long buttonPermissionId, Collection<Long> apiPermissionIds, Long actorUserId);

    List<ApiPermissionRule> findEnabledApiRules();

}
