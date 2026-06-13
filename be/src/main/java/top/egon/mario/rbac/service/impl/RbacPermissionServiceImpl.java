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
import top.egon.mario.rbac.dto.request.PermissionRequest;
import top.egon.mario.rbac.dto.response.MenuTreeResponse;
import top.egon.mario.rbac.dto.response.PermissionResponse;
import top.egon.mario.rbac.po.ApiPo;
import top.egon.mario.rbac.po.ButtonApiPo;
import top.egon.mario.rbac.po.ButtonPo;
import top.egon.mario.rbac.po.MenuPo;
import top.egon.mario.rbac.po.PermissionPo;
import top.egon.mario.rbac.po.enums.ButtonApiRelationType;
import top.egon.mario.rbac.po.enums.PermissionStatus;
import top.egon.mario.rbac.po.enums.PermissionType;
import top.egon.mario.rbac.repository.ApiRepository;
import top.egon.mario.rbac.repository.ButtonApiRepository;
import top.egon.mario.rbac.repository.ButtonRepository;
import top.egon.mario.rbac.repository.MenuRepository;
import top.egon.mario.rbac.repository.PermissionRepository;
import top.egon.mario.rbac.repository.RolePermissionRepository;
import top.egon.mario.rbac.service.RbacAuditService;
import top.egon.mario.rbac.service.RbacException;
import top.egon.mario.rbac.service.RbacPermissionService;
import top.egon.mario.rbac.service.RbacPermissionVersionService;
import top.egon.mario.rbac.service.model.ApiPermissionRule;
import top.egon.mario.rbac.service.model.RbacPermissionChangedEvent;
import top.egon.mario.rbac.service.security.RbacPublicApiPolicy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Permission management service for MENU, BUTTON and API resources.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RbacPermissionServiceImpl implements RbacPermissionService {

    private final PermissionRepository permissionRepository;
    private final MenuRepository menuRepository;
    private final ButtonRepository buttonRepository;
    private final ApiRepository apiRepository;
    private final ButtonApiRepository buttonApiRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final RbacDtoConverter rbacDtoConverter;
    private final RbacAuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final RbacPermissionVersionService permissionVersionService;

    @Override
    @Transactional
    public PermissionResponse createPermission(PermissionRequest request, Long actorUserId) {
        if (permissionRepository.existsByPermCodeAndDeletedFalse(request.getPermCode())) {
            throw new RbacException("RBAC_PERMISSION_CODE_DUPLICATED", "permission code already exists");
        }
        PermissionPo permission = new PermissionPo();
        applyPermissionFields(permission, request);
        PermissionPo savedPermission = permissionRepository.save(permission);
        saveDetail(savedPermission, request, actorUserId);
        auditService.log(actorUserId, "RBAC_PERMISSION_CREATE", "PERMISSION", savedPermission.getId(), null, savedPermission.getPermCode(), null, null);
        publishPermissionChanged("create permission");
        LogUtil.info(log).log("rbac permission created, permissionId={}, permCode={}, actorUserId={}",
                savedPermission.getId(), savedPermission.getPermCode(), actorUserId);
        return getPermission(savedPermission.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public PermissionPo getPermissionPo(Long permissionId) {
        return permissionRepository.findByIdAndDeletedFalse(permissionId)
                .orElseThrow(() -> new RbacException("RBAC_PERMISSION_NOT_FOUND", "permission not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public PermissionResponse getPermission(Long permissionId) {
        PermissionPo permission = getPermissionPo(permissionId);
        MenuPo menu = menuRepository.findById(permissionId).orElse(null);
        ButtonPo button = buttonRepository.findById(permissionId).orElse(null);
        ApiPo api = apiRepository.findById(permissionId).orElse(null);
        Set<Long> apiPermissionIds = button == null ? Set.of() : buttonApiRepository.findByButtonPermissionId(permissionId).stream()
                .map(ButtonApiPo::getApiPermissionId)
                .collect(Collectors.toSet());
        return rbacDtoConverter.toPermissionResponse(permission, menu, button, api, apiPermissionIds);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PermissionResponse> getPermissionPage(Pageable pageable) {
        return permissionRepository.findAll((root, query, cb) -> cb.isFalse(root.get("deleted")), pageable)
                .map(permission -> getPermission(permission.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PermissionResponse> getApiPermissionPage(Pageable pageable) {
        return permissionRepository.findAll((root, query, cb) -> cb.and(
                        cb.isFalse(root.get("deleted")),
                        cb.equal(root.get("permType"), PermissionType.API)
                ), pageable)
                .map(permission -> getPermission(permission.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MenuTreeResponse> getMenuTree() {
        List<PermissionPo> menuPermissions = permissionRepository
                .findByPermTypeAndDeletedFalseAndStatus(PermissionType.MENU, PermissionStatus.ENABLED)
                .stream()
                .sorted(Comparator.comparingInt(PermissionPo::getSortNo).thenComparing(PermissionPo::getId))
                .toList();
        return buildMenuTree(menuPermissions);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PermissionResponse> getButtonsByMenuId(Long menuPermissionId) {
        return buttonRepository.findByMenuPermissionId(menuPermissionId).stream()
                .map(button -> getPermission(button.getPermissionId()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> getButtonApiIds(Long buttonPermissionId) {
        return buttonApiRepository.findByButtonPermissionId(buttonPermissionId).stream()
                .map(ButtonApiPo::getApiPermissionId)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional
    public PermissionResponse updatePermission(Long permissionId, PermissionRequest request, Long actorUserId) {
        PermissionPo permission = getPermissionPo(permissionId);
        if (!permission.getPermCode().equals(request.getPermCode())
                && permissionRepository.existsByPermCodeAndDeletedFalse(request.getPermCode())) {
            throw new RbacException("RBAC_PERMISSION_CODE_DUPLICATED", "permission code already exists");
        }
        validatePermissionTypeChange(permission, rbacDtoConverter.toPoPermissionType(request.getPermType()));
        applyPermissionFields(permission, request);
        permissionRepository.save(permission);
        clearDetail(permissionId);
        saveDetail(permission, request, actorUserId);
        auditService.log(actorUserId, "RBAC_PERMISSION_UPDATE", "PERMISSION", permissionId, null, permission.getPermCode(), null, null);
        permissionVersionService.bumpRolesByPermissionIds(List.of(permissionId));
        publishPermissionChanged("update permission");
        LogUtil.info(log).log("rbac permission updated, permissionId={}, permCode={}, actorUserId={}",
                permissionId, permission.getPermCode(), actorUserId);
        return getPermission(permissionId);
    }

    @Override
    @Transactional
    public void updateStatus(Long permissionId, top.egon.mario.rbac.dto.enums.PermissionStatus status) {
        PermissionPo permission = getPermissionPo(permissionId);
        PermissionStatus permissionStatus = rbacDtoConverter.toPoPermissionStatus(status);
        permission.setStatus(permissionStatus);
        permissionRepository.save(permission);
        auditService.log(0L, "RBAC_PERMISSION_STATUS_UPDATE", "PERMISSION", permissionId, null, permissionStatus.name(), null, null);
        permissionVersionService.bumpRolesByPermissionIds(List.of(permissionId));
        publishPermissionChanged("update permission status");
        LogUtil.info(log).log("rbac permission status updated, permissionId={}, statusCode={}",
                permissionId, permissionStatus.getCode());
    }

    @Override
    @Transactional
    public void deletePermission(Long permissionId) {
        PermissionPo permission = getPermissionPo(permissionId);
        if (permissionRepository.existsByParentIdAndDeletedFalse(permissionId)
                || rolePermissionRepository.existsByPermissionId(permissionId)
                || buttonApiRepository.existsByButtonPermissionIdOrApiPermissionId(permissionId, permissionId)
                || menuRepository.existsByParentMenuId(permissionId)
                || buttonRepository.existsByMenuPermissionId(permissionId)) {
            throw new RbacException("RBAC_PERMISSION_IN_USE", "permission is still referenced");
        }
        clearDetail(permissionId);
        permission.setDeleted(true);
        permissionRepository.save(permission);
        auditService.log(0L, "RBAC_PERMISSION_DELETE", "PERMISSION", permissionId, permission.getPermCode(), null, null, null);
        publishPermissionChanged("delete permission");
        LogUtil.info(log).log("rbac permission deleted, permissionId={}, permCode={}",
                permissionId, permission.getPermCode());
    }

    @Override
    @Transactional
    public void replaceButtonApis(Long buttonPermissionId, Collection<Long> apiPermissionIds, Long actorUserId) {
        PermissionPo buttonPermission = getPermissionPo(buttonPermissionId);
        if (buttonPermission.getPermType() != PermissionType.BUTTON) {
            throw new RbacException("RBAC_PERMISSION_TYPE_INVALID", "permission is not a button");
        }
        Set<Long> requestedApiIds = apiPermissionIds == null ? Set.of() : new HashSet<>(apiPermissionIds);
        validateApiPermissions(requestedApiIds);

        Set<Long> oldApiIds = buttonApiRepository.findByButtonPermissionId(buttonPermissionId).stream()
                .map(ButtonApiPo::getApiPermissionId)
                .collect(Collectors.toSet());
        Set<Long> removedApiIds = new HashSet<>(oldApiIds);
        removedApiIds.removeAll(requestedApiIds);
        Set<Long> addedApiIds = new HashSet<>(requestedApiIds);
        addedApiIds.removeAll(oldApiIds);
        if (!removedApiIds.isEmpty()) {
            buttonApiRepository.deleteByButtonPermissionIdAndApiPermissionIdIn(buttonPermissionId, removedApiIds);
        }
        saveButtonApiLinks(buttonPermissionId, addedApiIds, actorUserId);
        auditService.log(actorUserId, "RBAC_BUTTON_API_UPDATE", "PERMISSION", buttonPermissionId, oldApiIds.toString(), requestedApiIds.toString(), null, null);
        if (!removedApiIds.isEmpty() || !addedApiIds.isEmpty()) {
            permissionVersionService.bumpRolesByPermissionIds(Set.of(buttonPermissionId));
        }
        publishPermissionChanged("update button api links");
        LogUtil.info(log).log("rbac button api links replaced, buttonPermissionId={}, apiPermissionCount={}, actorUserId={}",
                buttonPermissionId, requestedApiIds.size(), actorUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApiPermissionRule> findEnabledApiRules() {
        return apiRepository.findEnabledRules();
    }

    private void applyPermissionFields(PermissionPo permission, PermissionRequest request) {
        if (!StringUtils.hasText(request.getPermCode())) {
            throw new RbacException("RBAC_PERMISSION_CODE_REQUIRED", "permission code is required");
        }
        if (!StringUtils.hasText(request.getPermName())) {
            throw new RbacException("RBAC_PERMISSION_NAME_REQUIRED", "permission name is required");
        }
        PermissionPo mappedPermission = rbacDtoConverter.toPermissionPo(request);
        permission.setPermCode(request.getPermCode().trim());
        permission.setPermName(request.getPermName().trim());
        permission.setPermType(mappedPermission.getPermType());
        permission.setParentId(mappedPermission.getParentId());
        permission.setStatus(request.getStatus() == null ? PermissionStatus.ENABLED : mappedPermission.getStatus());
        permission.setSortNo(mappedPermission.getSortNo());
        permission.setDescription(mappedPermission.getDescription());
    }

    private void saveDetail(PermissionPo permission, PermissionRequest request, Long actorUserId) {
        switch (permission.getPermType()) {
            case MENU -> saveMenuDetail(permission.getId(), request.getMenu());
            case BUTTON -> saveButtonDetail(permission.getId(), request.getButton(), actorUserId);
            case API -> saveApiDetail(permission.getId(), request.getApi());
        }
    }

    private void saveMenuDetail(Long permissionId, PermissionRequest.Menu menuRequest) {
        if (menuRequest == null) {
            throw new RbacException("RBAC_MENU_DETAIL_REQUIRED", "menu detail is required");
        }
        MenuPo menu = rbacDtoConverter.toMenuPo(menuRequest);
        menu.setPermissionId(permissionId);
        menuRepository.save(menu);
    }

    private void saveButtonDetail(Long permissionId, PermissionRequest.Button buttonRequest, Long actorUserId) {
        if (buttonRequest == null || buttonRequest.getMenuPermissionId() == null || !StringUtils.hasText(buttonRequest.getButtonKey())) {
            throw new RbacException("RBAC_BUTTON_DETAIL_REQUIRED", "button detail is required");
        }
        PermissionPo menuPermission = getPermissionPo(buttonRequest.getMenuPermissionId());
        if (menuPermission.getPermType() != PermissionType.MENU) {
            throw new RbacException("RBAC_BUTTON_MENU_INVALID", "button must belong to a menu permission");
        }
        ButtonPo button = rbacDtoConverter.toButtonPo(buttonRequest);
        button.setPermissionId(permissionId);
        buttonRepository.save(button);
        validateApiPermissions(buttonRequest.getApiPermissionIds());
        saveButtonApiLinks(permissionId, buttonRequest.getApiPermissionIds(), actorUserId);
    }

    private void saveApiDetail(Long permissionId, PermissionRequest.Api apiRequest) {
        if (apiRequest == null || !StringUtils.hasText(apiRequest.getHttpMethod()) || !StringUtils.hasText(apiRequest.getUrlPattern())) {
            throw new RbacException("RBAC_API_DETAIL_REQUIRED", "api detail is required");
        }
        ApiPo api = rbacDtoConverter.toApiPo(apiRequest);
        api.setPermissionId(permissionId);
        api.setHttpMethod(apiRequest.getHttpMethod().trim().toUpperCase());
        api.setUrlPattern(apiRequest.getUrlPattern().trim());
        if (api.isPublicFlag() && !RbacPublicApiPolicy.isAllowedPublicRule(api.getHttpMethod(), api.getUrlPattern())) {
            throw new RbacException("RBAC_PUBLIC_API_NOT_ALLOWED", "public API path is not allowed");
        }
        apiRepository.save(api);
    }

    private void saveButtonApiLinks(Long buttonPermissionId, Collection<Long> apiPermissionIds, Long actorUserId) {
        if (apiPermissionIds == null || apiPermissionIds.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        buttonApiRepository.saveAll(apiPermissionIds.stream()
                .map(apiPermissionId -> {
                    ButtonApiPo link = new ButtonApiPo();
                    link.setButtonPermissionId(buttonPermissionId);
                    link.setApiPermissionId(apiPermissionId);
                    link.setRelationType(ButtonApiRelationType.CALLS);
                    link.setCreatedAt(now);
                    link.setCreatedBy(actorUserId);
                    return link;
                })
                .toList());
    }

    private void validateApiPermissions(Collection<Long> apiPermissionIds) {
        if (apiPermissionIds == null || apiPermissionIds.isEmpty()) {
            return;
        }
        List<PermissionPo> apiPermissions = permissionRepository.findByIdInAndDeletedFalse(apiPermissionIds).stream()
                .filter(permission -> permission.getPermType() == PermissionType.API)
                .toList();
        if (apiPermissions.size() != new HashSet<>(apiPermissionIds).size()) {
            throw new RbacException("RBAC_API_PERMISSION_NOT_FOUND", "api permission not found");
        }
    }

    private void validatePermissionTypeChange(PermissionPo permission, PermissionType requestedType) {
        if (permission.getPermType() == requestedType) {
            return;
        }
        Long permissionId = permission.getId();
        if (permissionRepository.existsByParentIdAndDeletedFalse(permissionId)
                || rolePermissionRepository.existsByPermissionId(permissionId)
                || buttonApiRepository.existsByButtonPermissionIdOrApiPermissionId(permissionId, permissionId)
                || menuRepository.existsByParentMenuId(permissionId)
                || buttonRepository.existsByMenuPermissionId(permissionId)) {
            throw new RbacException("RBAC_PERMISSION_IN_USE", "permission is still referenced");
        }
    }

    private List<MenuTreeResponse> buildMenuTree(List<PermissionPo> menuPermissions) {
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
            if (permission.getParentId() != null && nodeById.containsKey(permission.getParentId())) {
                nodeById.get(permission.getParentId()).getChildren().add(node);
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

    private void clearDetail(Long permissionId) {
        buttonApiRepository.deleteByButtonPermissionId(permissionId);
        if (menuRepository.existsById(permissionId)) {
            menuRepository.deleteById(permissionId);
        }
        if (buttonRepository.existsById(permissionId)) {
            buttonRepository.deleteById(permissionId);
        }
        if (apiRepository.existsById(permissionId)) {
            apiRepository.deleteById(permissionId);
        }
    }

    private void publishPermissionChanged(String reason) {
        eventPublisher.publishEvent(new RbacPermissionChangedEvent(reason));
    }

}
