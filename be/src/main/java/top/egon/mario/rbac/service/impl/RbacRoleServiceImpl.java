package top.egon.mario.rbac.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.rbac.converter.RbacDtoConverter;
import top.egon.mario.rbac.dto.request.CreateRoleRequest;
import top.egon.mario.rbac.dto.request.UpdateRoleRequest;
import top.egon.mario.rbac.dto.response.RoleResponse;
import top.egon.mario.rbac.po.PermissionPo;
import top.egon.mario.rbac.po.RoleInheritancePo;
import top.egon.mario.rbac.po.RolePermissionPo;
import top.egon.mario.rbac.po.RolePo;
import top.egon.mario.rbac.po.enums.PermissionStatus;
import top.egon.mario.rbac.po.enums.RbacStatus;
import top.egon.mario.rbac.repository.ButtonApiRepository;
import top.egon.mario.rbac.repository.PermissionRepository;
import top.egon.mario.rbac.repository.RoleInheritanceRepository;
import top.egon.mario.rbac.repository.RolePermissionRepository;
import top.egon.mario.rbac.repository.RoleRepository;
import top.egon.mario.rbac.repository.UserRoleRepository;
import top.egon.mario.rbac.service.RbacAuditService;
import top.egon.mario.rbac.service.RbacException;
import top.egon.mario.rbac.service.RbacPermissionVersionService;
import top.egon.mario.rbac.service.RbacRoleService;
import top.egon.mario.rbac.service.RoleHierarchyResolver;
import top.egon.mario.rbac.service.RolePermissionMergeService;
import top.egon.mario.rbac.service.model.ButtonApiLink;
import top.egon.mario.rbac.service.model.RbacPermissionChangedEvent;
import top.egon.mario.rbac.service.model.RoleInheritanceEdge;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Role management service for metadata, inheritance and permission grants.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RbacRoleServiceImpl implements RbacRoleService {

    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleInheritanceRepository roleInheritanceRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final ButtonApiRepository buttonApiRepository;
    private final RoleHierarchyResolver roleHierarchyResolver;
    private final RolePermissionMergeService rolePermissionMergeService;
    private final RbacDtoConverter rbacDtoConverter;
    private final RbacAuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final RbacPermissionVersionService permissionVersionService;

    @Override
    @Transactional
    public RoleResponse createRole(CreateRoleRequest request) {
        String roleCode = normalizeRoleCode(request.getRoleCode());
        if (roleRepository.existsByRoleCodeAndDeletedFalse(roleCode)) {
            throw new RbacException("RBAC_ROLE_CODE_DUPLICATED", "role code already exists");
        }
        RolePo role = rbacDtoConverter.toRolePo(request);
        role.setRoleCode(roleCode);
        role.setStatus(request.getStatus() == null ? RbacStatus.ENABLED : rbacDtoConverter.toPoRbacStatus(request.getStatus()));
        RolePo savedRole = roleRepository.save(role);
        auditService.log(0L, "RBAC_ROLE_CREATE", "ROLE", savedRole.getId(), null, savedRole.getRoleCode(), null, null);
        LogUtil.info(log).log("rbac role created, roleId={}, roleCode={}", savedRole.getId(), savedRole.getRoleCode());
        return rbacDtoConverter.toRoleResponse(savedRole);
    }

    @Override
    @Transactional(readOnly = true)
    public RolePo getRolePo(Long roleId) {
        return roleRepository.findByIdAndDeletedFalse(roleId)
                .orElseThrow(() -> new RbacException("RBAC_ROLE_NOT_FOUND", "role not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public RoleResponse getRole(Long roleId) {
        return rbacDtoConverter.toRoleResponse(getRolePo(roleId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RoleResponse> getRolePage(Pageable pageable) {
        return roleRepository.findAll((root, query, cb) -> cb.isFalse(root.get("deleted")), pageable)
                .map(rbacDtoConverter::toRoleResponse);
    }

    @Override
    @Transactional
    public RoleResponse updateRole(Long roleId, UpdateRoleRequest request) {
        RolePo role = getRolePo(roleId);
        if (role.isBuiltIn() && request.getBuiltIn() != null && !request.getBuiltIn()) {
            throw new RbacException("RBAC_ROLE_BUILT_IN", "built-in role key fields cannot be changed");
        }
        if (StringUtils.hasText(request.getRoleName())) {
            role.setRoleName(request.getRoleName());
        }
        boolean statusChanged = false;
        if (request.getStatus() != null) {
            RbacStatus status = rbacDtoConverter.toPoRbacStatus(request.getStatus());
            statusChanged = role.getStatus() != status;
            role.setStatus(status);
        }
        if (request.getSortNo() != null) {
            role.setSortNo(request.getSortNo());
        }
        if (request.getBuiltIn() != null) {
            role.setBuiltIn(request.getBuiltIn());
        }
        role.setDescription(request.getDescription());
        RolePo savedRole = roleRepository.save(role);
        if (statusChanged) {
            permissionVersionService.bumpRolePermissionVersion(roleId);
        }
        publishPermissionChanged("update role");
        LogUtil.info(log).log("rbac role updated, roleId={}, roleCode={}", roleId, savedRole.getRoleCode());
        return rbacDtoConverter.toRoleResponse(savedRole);
    }

    @Override
    @Transactional
    public void deleteRole(Long roleId) {
        RolePo role = getRolePo(roleId);
        if (role.isBuiltIn()) {
            throw new RbacException("RBAC_ROLE_BUILT_IN", "built-in role cannot be deleted");
        }
        if (userRoleRepository.existsByRoleId(roleId) || roleInheritanceRepository.existsByRoleIdOrInheritedRoleId(roleId, roleId)
                || !rolePermissionRepository.findByRoleId(roleId).isEmpty()) {
            throw new RbacException("RBAC_ROLE_IN_USE", "role is still referenced");
        }
        role.setDeleted(true);
        roleRepository.save(role);
        auditService.log(0L, "RBAC_ROLE_DELETE", "ROLE", roleId, role.getRoleCode(), null, null, null);
        publishPermissionChanged("delete role");
        LogUtil.info(log).log("rbac role deleted, roleId={}, roleCode={}", roleId, role.getRoleCode());
    }

    @Override
    @Transactional
    public void replaceRoleInheritance(Long roleId, Collection<Long> inheritedRoleIds, Long actorUserId) {
        getRolePo(roleId);
        Set<Long> requestedRoleIds = inheritedRoleIds == null ? Set.of() : new HashSet<>(inheritedRoleIds);
        validateRolesExist(requestedRoleIds);

        List<RoleInheritanceEdge> existingEdges = roleInheritanceRepository.findAll().stream()
                .filter(edge -> !edge.getRoleId().equals(roleId))
                .map(edge -> new RoleInheritanceEdge(edge.getRoleId(), edge.getInheritedRoleId()))
                .toList();
        roleHierarchyResolver.assertNoCycle(roleId, requestedRoleIds, existingEdges);

        Set<Long> oldRoleIds = roleInheritanceRepository.findByRoleId(roleId).stream()
                .map(RoleInheritancePo::getInheritedRoleId)
                .collect(Collectors.toSet());
        Set<Long> removedRoleIds = new HashSet<>(oldRoleIds);
        removedRoleIds.removeAll(requestedRoleIds);
        Set<Long> addedRoleIds = new HashSet<>(requestedRoleIds);
        addedRoleIds.removeAll(oldRoleIds);

        if (!removedRoleIds.isEmpty()) {
            roleInheritanceRepository.deleteByRoleIdAndInheritedRoleIdIn(roleId, removedRoleIds);
        }
        Instant now = Instant.now();
        roleInheritanceRepository.saveAll(addedRoleIds.stream()
                .map(inheritedRoleId -> {
                    RoleInheritancePo relation = new RoleInheritancePo();
                    relation.setRoleId(roleId);
                    relation.setInheritedRoleId(inheritedRoleId);
                    relation.setCreatedAt(now);
                    relation.setCreatedBy(actorUserId);
                    return relation;
                })
                .toList());
        auditService.log(actorUserId, "RBAC_ROLE_INHERITANCE_UPDATE", "ROLE", roleId, oldRoleIds.toString(), requestedRoleIds.toString(), null, null);
        if (!removedRoleIds.isEmpty() || !addedRoleIds.isEmpty()) {
            permissionVersionService.bumpRolePermissionVersion(roleId);
        }
        publishPermissionChanged("update role inheritance");
        LogUtil.info(log).log("rbac role inheritance replaced, roleId={}, inheritedRoleCount={}, actorUserId={}",
                roleId, requestedRoleIds.size(), actorUserId);
    }

    @Override
    @Transactional
    public Set<Long> replaceRolePermissions(Long roleId, Collection<Long> permissionIds, boolean syncButtonApis,
                                            Long actorUserId) {
        getRolePo(roleId);
        Set<Long> requestedPermissionIds = permissionIds == null ? Set.of() : new HashSet<>(permissionIds);
        validatePermissionsExist(requestedPermissionIds);
        Set<Long> mergedPermissionIds = rolePermissionMergeService.mergeButtonApis(
                requestedPermissionIds,
                syncButtonApis,
                buttonApiLinks(requestedPermissionIds)
        );

        Set<Long> oldPermissionIds = rolePermissionRepository.findByRoleId(roleId).stream()
                .map(RolePermissionPo::getPermissionId)
                .collect(Collectors.toSet());
        Set<Long> removedPermissionIds = new HashSet<>(oldPermissionIds);
        removedPermissionIds.removeAll(mergedPermissionIds);
        Set<Long> addedPermissionIds = new HashSet<>(mergedPermissionIds);
        addedPermissionIds.removeAll(oldPermissionIds);

        if (!removedPermissionIds.isEmpty()) {
            rolePermissionRepository.deleteByRoleIdAndPermissionIdIn(roleId, removedPermissionIds);
        }
        Instant now = Instant.now();
        rolePermissionRepository.saveAll(addedPermissionIds.stream()
                .map(permissionId -> {
                    RolePermissionPo relation = new RolePermissionPo();
                    relation.setRoleId(roleId);
                    relation.setPermissionId(permissionId);
                    relation.setGrantedAt(now);
                    relation.setGrantedBy(actorUserId);
                    return relation;
                })
                .toList());
        auditService.log(actorUserId, "RBAC_ROLE_PERMISSION_UPDATE", "ROLE", roleId, oldPermissionIds.toString(), mergedPermissionIds.toString(), null, null);
        if (!removedPermissionIds.isEmpty() || !addedPermissionIds.isEmpty()) {
            permissionVersionService.bumpRolePermissionVersion(roleId);
        }
        publishPermissionChanged("update role permissions");
        LogUtil.info(log).log("rbac role permissions replaced, roleId={}, permissionCount={}, actorUserId={}",
                roleId, mergedPermissionIds.size(), actorUserId);
        return mergedPermissionIds;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> getEffectivePermissionIds(Long roleId) {
        getRolePo(roleId);
        List<RoleInheritanceEdge> enabledEdges = roleInheritanceRepository.findAll().stream()
                .map(edge -> new RoleInheritanceEdge(edge.getRoleId(), edge.getInheritedRoleId()))
                .toList();
        Set<Long> effectiveRoleIds = roleHierarchyResolver.resolveEffectiveRoleIds(Set.of(roleId), enabledEdges);
        return rolePermissionRepository.findByRoleIdIn(effectiveRoleIds).stream()
                .map(RolePermissionPo::getPermissionId)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> getDirectPermissionIds(Long roleId) {
        return rolePermissionRepository.findByRoleId(roleId).stream()
                .map(RolePermissionPo::getPermissionId)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> getInheritedRoleIds(Long roleId) {
        return roleInheritanceRepository.findByRoleId(roleId).stream()
                .map(RoleInheritancePo::getInheritedRoleId)
                .collect(Collectors.toSet());
    }

    private List<ButtonApiLink> buttonApiLinks(Set<Long> permissionIds) {
        if (permissionIds.isEmpty()) {
            return List.of();
        }
        return buttonApiRepository.findByButtonPermissionIdIn(permissionIds).stream()
                .map(link -> new ButtonApiLink(link.getButtonPermissionId(), link.getApiPermissionId()))
                .toList();
    }

    private void validateRolesExist(Set<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return;
        }
        List<RolePo> roles = roleRepository.findByIdInAndDeletedFalse(roleIds);
        if (roles.size() != roleIds.size()) {
            throw new RbacException("RBAC_ROLE_NOT_FOUND", "role not found");
        }
    }

    private void validatePermissionsExist(Set<Long> permissionIds) {
        if (permissionIds.isEmpty()) {
            return;
        }
        List<PermissionPo> permissions = permissionRepository.findByIdInAndDeletedFalseAndStatus(permissionIds, PermissionStatus.ENABLED);
        if (permissions.size() != permissionIds.size()) {
            throw new RbacException("RBAC_PERMISSION_NOT_FOUND", "permission not found or disabled");
        }
    }

    private String normalizeRoleCode(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            throw new RbacException("RBAC_ROLE_CODE_REQUIRED", "role code is required");
        }
        return roleCode.trim().toUpperCase();
    }

    private void publishPermissionChanged(String reason) {
        eventPublisher.publishEvent(new RbacPermissionChangedEvent(reason));
    }

}
