package top.egon.mario.rbac.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.rbac.dto.response.EffectivePermissionResponse;
import top.egon.mario.rbac.dto.response.MenuTreeResponse;
import top.egon.mario.rbac.po.MenuPo;
import top.egon.mario.rbac.po.PermissionPo;
import top.egon.mario.rbac.po.RolePermissionPo;
import top.egon.mario.rbac.po.RolePo;
import top.egon.mario.rbac.po.enums.PermissionStatus;
import top.egon.mario.rbac.po.enums.PermissionType;
import top.egon.mario.rbac.po.enums.RbacStatus;
import top.egon.mario.rbac.repository.MenuRepository;
import top.egon.mario.rbac.repository.PermissionRepository;
import top.egon.mario.rbac.repository.RoleInheritanceRepository;
import top.egon.mario.rbac.repository.RolePermissionRepository;
import top.egon.mario.rbac.repository.RoleRepository;
import top.egon.mario.rbac.repository.UserRoleRepository;
import top.egon.mario.rbac.service.RbacEffectivePermissionService;
import top.egon.mario.rbac.service.RoleHierarchyResolver;
import top.egon.mario.rbac.service.cache.RbacPermissionRedisCache;
import top.egon.mario.rbac.service.model.RoleInheritanceEdge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Calculates effective roles, permissions and menus after RBAC1 inheritance expansion.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Validated
public class RbacEffectivePermissionServiceImpl implements RbacEffectivePermissionService {

    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final RoleInheritanceRepository roleInheritanceRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final MenuRepository menuRepository;
    private final RoleHierarchyResolver roleHierarchyResolver;
    private final RbacPermissionRedisCache permissionRedisCache;

    @Override
    @Transactional(readOnly = true)
    public EffectivePermissionResponse getUserEffectivePermissions(Long userId) {
        return permissionRedisCache.getUserEffectivePermissions(userId, () -> calculateUserEffectivePermissions(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MenuTreeResponse> getUserMenuTree(Long userId) {
        return permissionRedisCache.getUserMenuTree(userId, () -> calculateUserMenuTree(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> resolveEffectiveRoleIds(Long userId) {
        Set<Long> directRoleIds = userRoleRepository.findByUserId(userId).stream()
                .map(userRole -> userRole.getRoleId())
                .collect(Collectors.toSet());
        if (directRoleIds.isEmpty()) {
            return Set.of();
        }

        Set<Long> enabledRoleIds = roleRepository.findByIdInAndDeletedFalseAndStatus(directRoleIds, RbacStatus.ENABLED)
                .stream()
                .map(RolePo::getId)
                .collect(Collectors.toSet());
        List<RoleInheritanceEdge> enabledEdges = enabledInheritanceEdges();
        return roleHierarchyResolver.resolveEffectiveRoleIds(enabledRoleIds, enabledEdges);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> getUserApiAuthorities(Long userId) {
        return getUserEffectivePermissions(userId).apiCodes();
    }

    private EffectivePermissionResponse calculateUserEffectivePermissions(Long userId) {
        Set<Long> effectiveRoleIds = resolveEffectiveRoleIds(userId);
        List<RolePo> roles = roleRepository.findByIdInAndDeletedFalseAndStatus(effectiveRoleIds, RbacStatus.ENABLED);
        Set<String> roleCodes = roles.stream()
                .map(RolePo::getRoleCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<PermissionPo> permissions = findEnabledPermissionsByRoleIds(effectiveRoleIds);
        EffectivePermissionResponse response = EffectivePermissionResponse.builder()
                .roleIds(effectiveRoleIds)
                .roleCodes(roleCodes)
                .menuCodes(codesByType(permissions, PermissionType.MENU))
                .buttonCodes(codesByType(permissions, PermissionType.BUTTON))
                .apiCodes(codesByType(permissions, PermissionType.API))
                .build();
        LogUtil.debug(log).log("rbac effective permissions calculated, userId={}, roleCount={}, permissionCount={}",
                userId, effectiveRoleIds.size(), permissions.size());
        return response;
    }

    private List<MenuTreeResponse> calculateUserMenuTree(Long userId) {
        Set<Long> effectiveRoleIds = resolveEffectiveRoleIds(userId);
        List<PermissionPo> menuPermissions = findEnabledPermissionsByRoleIds(effectiveRoleIds).stream()
                .filter(permission -> permission.getPermType() == PermissionType.MENU)
                .sorted(Comparator.comparingInt(PermissionPo::getSortNo).thenComparing(PermissionPo::getId))
                .toList();
        LogUtil.debug(log).log("rbac user menu tree calculated, userId={}, menuPermissionCount={}",
                userId, menuPermissions.size());
        return buildMenuTree(menuPermissions);
    }

    private List<PermissionPo> findEnabledPermissionsByRoleIds(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        Set<Long> permissionIds = rolePermissionRepository.findByRoleIdIn(roleIds).stream()
                .map(RolePermissionPo::getPermissionId)
                .collect(Collectors.toSet());
        if (permissionIds.isEmpty()) {
            return List.of();
        }
        return permissionRepository.findByIdInAndDeletedFalseAndStatus(permissionIds, PermissionStatus.ENABLED);
    }

    private List<RoleInheritanceEdge> enabledInheritanceEdges() {
        Set<Long> enabledRoleIds = roleRepository.findAll().stream()
                .filter(role -> !role.isDeleted())
                .filter(role -> role.getStatus() == RbacStatus.ENABLED)
                .map(RolePo::getId)
                .collect(Collectors.toSet());
        return roleInheritanceRepository.findAll().stream()
                .filter(edge -> enabledRoleIds.contains(edge.getRoleId()))
                .filter(edge -> enabledRoleIds.contains(edge.getInheritedRoleId()))
                .map(edge -> new RoleInheritanceEdge(edge.getRoleId(), edge.getInheritedRoleId()))
                .toList();
    }

    private Set<String> codesByType(List<PermissionPo> permissions, PermissionType type) {
        return permissions.stream()
                .filter(permission -> permission.getPermType() == type)
                .map(PermissionPo::getPermCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<MenuTreeResponse> buildMenuTree(List<PermissionPo> menuPermissions) {
        if (menuPermissions.isEmpty()) {
            return List.of();
        }
        Map<Long, PermissionPo> permissionById = menuPermissions.stream()
                .collect(Collectors.toMap(PermissionPo::getId, Function.identity()));
        Map<Long, MenuPo> menuByPermissionId = menuRepository.findByPermissionIdIn(permissionById.keySet()).stream()
                .collect(Collectors.toMap(MenuPo::getPermissionId, Function.identity()));
        Map<Long, MenuTreeResponse> nodeById = new HashMap<>();
        for (PermissionPo permission : menuPermissions) {
            MenuPo menu = menuByPermissionId.get(permission.getId());
            if (menu == null) {
                continue;
            }
            MenuTreeResponse node = new MenuTreeResponse();
            node.setPermissionId(permission.getId());
            node.setPermCode(permission.getPermCode());
            node.setPermName(permission.getPermName());
            node.setSortNo(permission.getSortNo());
            node.setRouteName(menu.getRouteName());
            node.setRoutePath(menu.getRoutePath());
            node.setComponent(menu.getComponent());
            node.setRedirect(menu.getRedirect());
            node.setIcon(menu.getIcon());
            node.setHidden(menu.isHidden());
            node.setCacheable(menu.isCacheable());
            node.setExternalLink(menu.getExternalLink());
            nodeById.put(permission.getId(), node);
        }

        List<MenuTreeResponse> roots = new ArrayList<>();
        for (PermissionPo permission : menuPermissions) {
            MenuTreeResponse node = nodeById.get(permission.getId());
            if (node == null) {
                continue;
            }
            Long parentId = permission.getParentId();
            if (parentId != null && nodeById.containsKey(parentId)) {
                nodeById.get(parentId).getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        sortMenuNodes(roots);
        return roots;
    }

    private void sortMenuNodes(List<MenuTreeResponse> nodes) {
        nodes.sort(Comparator.comparingInt(MenuTreeResponse::getSortNo).thenComparing(MenuTreeResponse::getPermissionId));
        nodes.forEach(node -> sortMenuNodes(node.getChildren()));
    }

}
