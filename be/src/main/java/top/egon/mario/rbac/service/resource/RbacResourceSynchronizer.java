package top.egon.mario.rbac.service.resource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.rbac.po.ApiPo;
import top.egon.mario.rbac.po.ButtonApiPo;
import top.egon.mario.rbac.po.ButtonPo;
import top.egon.mario.rbac.po.MenuPo;
import top.egon.mario.rbac.po.PermissionPo;
import top.egon.mario.rbac.po.RolePermissionPo;
import top.egon.mario.rbac.po.RolePo;
import top.egon.mario.rbac.po.enums.ButtonApiRelationType;
import top.egon.mario.rbac.po.enums.PermissionStatus;
import top.egon.mario.rbac.po.enums.PermissionType;
import top.egon.mario.rbac.po.enums.RbacStatus;
import top.egon.mario.rbac.repository.ApiRepository;
import top.egon.mario.rbac.repository.ButtonApiRepository;
import top.egon.mario.rbac.repository.ButtonRepository;
import top.egon.mario.rbac.repository.MenuRepository;
import top.egon.mario.rbac.repository.PermissionRepository;
import top.egon.mario.rbac.repository.RolePermissionRepository;
import top.egon.mario.rbac.repository.RoleRepository;
import top.egon.mario.rbac.service.RbacPermissionVersionService;
import top.egon.mario.rbac.service.model.RbacPermissionChangedEvent;
import top.egon.mario.rbac.service.resource.model.RbacApiSeed;
import top.egon.mario.rbac.service.resource.model.RbacButtonSeed;
import top.egon.mario.rbac.service.resource.model.RbacMenuSeed;
import top.egon.mario.rbac.service.resource.model.RbacResourceSeed;
import top.egon.mario.rbac.service.resource.model.RbacResourceSource;
import top.egon.mario.rbac.service.resource.model.RbacRolePresetSeed;
import top.egon.mario.rbac.service.security.RbacPublicApiPolicy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Synchronizes declared RBAC resources into the unified permission tables.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RbacResourceSynchronizer {

    private static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";
    private static final String DIGEST_ALGORITHM = "SHA-256";

    private final PermissionRepository permissionRepository;
    private final MenuRepository menuRepository;
    private final ButtonRepository buttonRepository;
    private final ApiRepository apiRepository;
    private final ButtonApiRepository buttonApiRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final RbacPermissionVersionService permissionVersionService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Applies resource declarations for one app and append-only managed role presets.
     */
    @Transactional
    public void synchronize(String appCode, List<RbacResourceSeed> resources, List<RbacRolePresetSeed> rolePresets) {
        String ownerApp = requireText(appCode, "appCode");
        List<RbacResourceSeed> safeResources = resources == null ? List.of() : resources;
        List<RbacRolePresetSeed> safeRolePresets = rolePresets == null ? List.of() : rolePresets;
        validate(ownerApp, safeResources, safeRolePresets);

        Map<String, RbacResourceSeed> seedsByCode = safeResources.stream()
                .collect(Collectors.toMap(RbacResourceSeed::code, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<String, PermissionPo> permissionsByCode = new LinkedHashMap<>();
        Set<Long> affectedPermissionIds = new LinkedHashSet<>();

        synchronizeResourcesByType(ownerApp, seedsByCode, permissionsByCode, affectedPermissionIds, PermissionType.API);
        synchronizeMenus(ownerApp, seedsByCode, permissionsByCode, affectedPermissionIds);
        synchronizeResourcesByType(ownerApp, seedsByCode, permissionsByCode, affectedPermissionIds, PermissionType.BUTTON);
        synchronizeButtonApis(seedsByCode, permissionsByCode, affectedPermissionIds);

        Set<Long> changedRoleIds = synchronizeRolePresets(ownerApp, safeRolePresets, permissionsByCode);
        if (!affectedPermissionIds.isEmpty()) {
            permissionVersionService.bumpRolesByPermissionIds(affectedPermissionIds);
        }
        if (!changedRoleIds.isEmpty()) {
            permissionVersionService.bumpRolePermissionVersions(changedRoleIds);
        }
        if (!affectedPermissionIds.isEmpty() || !changedRoleIds.isEmpty()) {
            eventPublisher.publishEvent(new RbacPermissionChangedEvent("sync rbac resources for " + ownerApp));
        }
        LogUtil.info(log).log("rbac resource synchronization completed, appCode={}, resourceCount={}, rolePresetCount={}",
                ownerApp, safeResources.size(), safeRolePresets.size());
    }

    private void synchronizeResourcesByType(String ownerApp, Map<String, RbacResourceSeed> seedsByCode,
                                            Map<String, PermissionPo> permissionsByCode,
                                            Set<Long> affectedPermissionIds, PermissionType type) {
        seedsByCode.values().stream()
                .filter(seed -> seed.type() == type)
                .forEach(seed -> {
                    PermissionSyncResult result = upsertPermission(ownerApp, seed, permissionsByCode);
                    PermissionPo permission = result.permission();
                    boolean detailChanged = false;
                    if (!result.writable()) {
                        LogUtil.debug(log).log("rbac manual resource skipped, permCode={}", seed.code());
                    } else if (type == PermissionType.API) {
                        detailChanged = upsertApi(seed, permission);
                    } else if (type == PermissionType.BUTTON) {
                        detailChanged = upsertButton(seed, permission, permissionsByCode);
                    }
                    if (result.changed() || detailChanged) {
                        affectedPermissionIds.add(permission.getId());
                    }
                });
    }

    private void synchronizeMenus(String ownerApp, Map<String, RbacResourceSeed> seedsByCode,
                                  Map<String, PermissionPo> permissionsByCode, Set<Long> affectedPermissionIds) {
        seedsByCode.values().stream()
                .filter(seed -> seed.type() == PermissionType.MENU)
                .sorted(Comparator.comparingInt(seed -> menuDepth(seed, seedsByCode)))
                .forEach(seed -> {
                    PermissionSyncResult result = upsertPermission(ownerApp, seed, permissionsByCode);
                    boolean detailChanged = false;
                    if (!result.writable()) {
                        LogUtil.debug(log).log("rbac manual resource skipped, permCode={}", seed.code());
                    } else {
                        detailChanged = upsertMenu(seed, result.permission(), permissionsByCode);
                    }
                    if (result.changed() || detailChanged) {
                        affectedPermissionIds.add(result.permission().getId());
                    }
                });
    }

    private PermissionSyncResult upsertPermission(String ownerApp, RbacResourceSeed seed, Map<String, PermissionPo> permissionsByCode) {
        PermissionPo permission = permissionRepository.findByPermCodeAndDeletedFalse(seed.code()).orElseGet(PermissionPo::new);
        boolean created = permission.getId() == null;
        if (permission.getId() != null && permission.isManaged() && !ownerApp.equals(permission.getOwnerApp())) {
            throw new IllegalStateException("permission is managed by another app: " + seed.code());
        }
        if (permission.getId() != null && !permission.isManaged()) {
            permissionsByCode.put(permission.getPermCode(), permission);
            return new PermissionSyncResult(permission, false, false);
        }
        PermissionPo parent = null;
        if (StringUtils.hasText(seed.parentCode())) {
            parent = resolvePermission(seed.parentCode(), permissionsByCode);
        }
        String nextSyncHash = syncHash(seed);
        boolean changed = created
                || !permission.isManaged()
                || !Objects.equals(permission.getPermCode(), seed.code())
                || !Objects.equals(permission.getPermName(), seed.name())
                || permission.getPermType() != seed.type()
                || !Objects.equals(permission.getParentId(), parent == null ? null : parent.getId())
                || permission.getStatus() != (seed.status() == null ? PermissionStatus.ENABLED : seed.status())
                || permission.getSortNo() != seed.sortNo()
                || !Objects.equals(permission.getDescription(), seed.description())
                || !Objects.equals(permission.getOwnerApp(), ownerApp)
                || !Objects.equals(permission.getSourceType(), sourceName(seed.source()))
                || !Objects.equals(permission.getSourceKey(), sourceKey(ownerApp, seed.code()))
                || !Objects.equals(permission.getSyncHash(), nextSyncHash);
        permission.setPermCode(seed.code());
        permission.setPermName(seed.name());
        permission.setPermType(seed.type());
        permission.setParentId(parent == null ? null : parent.getId());
        permission.setStatus(seed.status() == null ? PermissionStatus.ENABLED : seed.status());
        permission.setSortNo(seed.sortNo());
        permission.setDescription(seed.description());
        permission.setManaged(true);
        permission.setOwnerApp(ownerApp);
        permission.setSourceType(sourceName(seed.source()));
        permission.setSourceKey(sourceKey(ownerApp, seed.code()));
        permission.setSyncHash(nextSyncHash);
        permission.setLastSyncedAt(Instant.now());
        permission.setLastSeenAt(Instant.now());
        permission.setDeleted(false);
        PermissionPo saved = permissionRepository.save(permission);
        permissionsByCode.put(saved.getPermCode(), saved);
        return new PermissionSyncResult(saved, changed, true);
    }

    private boolean upsertApi(RbacResourceSeed seed, PermissionPo permission) {
        RbacApiSeed apiSeed = seed.api();
        ApiPo api = apiRepository.findById(permission.getId()).orElseGet(ApiPo::new);
        boolean changed = api.getPermissionId() == null
                || !Objects.equals(api.getHttpMethod(), apiSeed.httpMethod())
                || !Objects.equals(api.getUrlPattern(), apiSeed.urlPattern())
                || api.getMatcherType() != apiSeed.matcherType()
                || api.isPublicFlag() != apiSeed.publicFlag()
                || !Objects.equals(api.getServiceTag(), StringUtils.hasText(seed.serviceTag()) ? seed.serviceTag() : seed.appCode())
                || !Objects.equals(api.getOperationName(), seed.name())
                || api.getRiskLevel() != apiSeed.riskLevel();
        api.setPermissionId(permission.getId());
        api.setHttpMethod(apiSeed.httpMethod());
        api.setUrlPattern(apiSeed.urlPattern());
        api.setMatcherType(apiSeed.matcherType());
        api.setPublicFlag(apiSeed.publicFlag());
        api.setServiceTag(StringUtils.hasText(seed.serviceTag()) ? seed.serviceTag() : seed.appCode());
        api.setOperationName(seed.name());
        api.setRiskLevel(apiSeed.riskLevel());
        api.setLastScannedAt(Instant.now());
        apiRepository.save(api);
        return changed;
    }

    private boolean upsertMenu(RbacResourceSeed seed, PermissionPo permission, Map<String, PermissionPo> permissionsByCode) {
        RbacMenuSeed menuSeed = seed.menu();
        PermissionPo parentMenu = null;
        if (StringUtils.hasText(seed.parentCode())) {
            parentMenu = resolvePermission(seed.parentCode(), permissionsByCode);
        }
        MenuPo menu = menuRepository.findById(permission.getId()).orElseGet(MenuPo::new);
        boolean changed = menu.getPermissionId() == null
                || !Objects.equals(menu.getParentMenuId(), parentMenu == null ? null : parentMenu.getId())
                || !Objects.equals(menu.getRouteName(), menuSeed.routeName())
                || !Objects.equals(menu.getRoutePath(), menuSeed.routePath())
                || !Objects.equals(menu.getComponent(), menuSeed.component())
                || !Objects.equals(menu.getRedirect(), menuSeed.redirect())
                || !Objects.equals(menu.getIcon(), menuSeed.icon())
                || menu.isHidden() != menuSeed.hidden()
                || menu.isCacheable() != menuSeed.cacheable()
                || !Objects.equals(menu.getExternalLink(), menuSeed.externalLink());
        menu.setPermissionId(permission.getId());
        menu.setParentMenuId(parentMenu == null ? null : parentMenu.getId());
        menu.setRouteName(menuSeed.routeName());
        menu.setRoutePath(menuSeed.routePath());
        menu.setComponent(menuSeed.component());
        menu.setRedirect(menuSeed.redirect());
        menu.setIcon(menuSeed.icon());
        menu.setHidden(menuSeed.hidden());
        menu.setCacheable(menuSeed.cacheable());
        menu.setExternalLink(menuSeed.externalLink());
        menuRepository.save(menu);
        return changed;
    }

    private boolean upsertButton(RbacResourceSeed seed, PermissionPo permission, Map<String, PermissionPo> permissionsByCode) {
        RbacButtonSeed buttonSeed = seed.button();
        PermissionPo menuPermission = resolvePermission(seed.parentCode(), permissionsByCode);
        ButtonPo button = buttonRepository.findById(permission.getId()).orElseGet(ButtonPo::new);
        boolean changed = button.getPermissionId() == null
                || !Objects.equals(button.getMenuPermissionId(), menuPermission.getId())
                || !Objects.equals(button.getButtonKey(), buttonSeed.buttonKey())
                || !Objects.equals(button.getFrontendAction(), buttonSeed.frontendAction())
                || !Objects.equals(button.getStyleHint(), buttonSeed.styleHint())
                || !Objects.equals(button.getDescription(), seed.description());
        button.setPermissionId(permission.getId());
        button.setMenuPermissionId(menuPermission.getId());
        button.setButtonKey(buttonSeed.buttonKey());
        button.setFrontendAction(buttonSeed.frontendAction());
        button.setStyleHint(buttonSeed.styleHint());
        button.setDescription(seed.description());
        buttonRepository.save(button);
        return changed;
    }

    private void synchronizeButtonApis(Map<String, RbacResourceSeed> seedsByCode, Map<String, PermissionPo> permissionsByCode,
                                       Set<Long> affectedPermissionIds) {
        seedsByCode.values().stream()
                .filter(seed -> seed.type() == PermissionType.BUTTON)
                .forEach(seed -> {
                    PermissionPo buttonPermission = resolvePermission(seed.code(), permissionsByCode);
                    if (!buttonPermission.isManaged()) {
                        LogUtil.debug(log).log("rbac manual button-api links skipped, permCode={}", seed.code());
                        return;
                    }
                    Set<Long> desiredApiIds = seed.buttonApiCodes().stream()
                            .map(code -> resolvePermission(code, permissionsByCode))
                            .map(PermissionPo::getId)
                            .collect(Collectors.toCollection(LinkedHashSet::new));
                    Map<Long, ButtonApiPo> linksByApiId = buttonApiRepository.findByButtonPermissionId(buttonPermission.getId()).stream()
                            .collect(Collectors.toMap(ButtonApiPo::getApiPermissionId, Function.identity()));
                    for (Long apiId : desiredApiIds) {
                        if (linksByApiId.containsKey(apiId)) {
                            continue;
                        }
                        ButtonApiPo link = new ButtonApiPo();
                        link.setButtonPermissionId(buttonPermission.getId());
                        link.setApiPermissionId(apiId);
                        link.setRelationType(ButtonApiRelationType.CALLS);
                        link.setCreatedAt(Instant.now());
                        buttonApiRepository.save(link);
                        affectedPermissionIds.add(buttonPermission.getId());
                        affectedPermissionIds.add(apiId);
                    }
                });
    }

    private Set<Long> synchronizeRolePresets(String ownerApp, List<RbacRolePresetSeed> rolePresets,
                                             Map<String, PermissionPo> permissionsByCode) {
        Set<Long> changedRoleIds = new LinkedHashSet<>();
        for (RbacRolePresetSeed seed : rolePresets) {
            RolePo role = roleRepository.findByRoleCodeAndDeletedFalse(seed.roleCode()).orElse(null);
            boolean created = false;
            if (role == null) {
                role = new RolePo();
                role.setRoleCode(seed.roleCode());
                role.setStatus(RbacStatus.ENABLED);
                role.setManaged(true);
                created = true;
            } else if (!role.isManaged()) {
                continue;
            }
            String nextSyncHash = syncHash(seed);
            boolean roleChanged = created
                    || !Objects.equals(role.getRoleName(), seed.roleName())
                    || role.getSortNo() != seed.sortNo()
                    || !Objects.equals(role.getDescription(), seed.description())
                    || !Objects.equals(role.getOwnerApp(), ownerApp)
                    || !Objects.equals(role.getSourceType(), sourceName(seed.source()))
                    || !Objects.equals(role.getSourceKey(), sourceKey(ownerApp, seed.roleCode()))
                    || !Objects.equals(role.getSyncHash(), nextSyncHash);
            role.setRoleName(seed.roleName());
            role.setSortNo(seed.sortNo());
            role.setDescription(seed.description());
            role.setOwnerApp(ownerApp);
            role.setSourceType(sourceName(seed.source()));
            role.setSourceKey(sourceKey(ownerApp, seed.roleCode()));
            role.setSyncHash(nextSyncHash);
            role.setLastSyncedAt(Instant.now());
            role = roleRepository.save(role);
            boolean grantsChanged = grantMissingPermissions(role, seed, permissionsByCode);
            if (roleChanged || grantsChanged) {
                changedRoleIds.add(role.getId());
            }
        }
        return changedRoleIds;
    }

    private boolean grantMissingPermissions(RolePo role, RbacRolePresetSeed seed, Map<String, PermissionPo> permissionsByCode) {
        Map<Long, RolePermissionPo> grantsByPermissionId = rolePermissionRepository.findByRoleId(role.getId()).stream()
                .collect(Collectors.toMap(RolePermissionPo::getPermissionId, Function.identity()));
        boolean changed = false;
        for (String permissionCode : seed.permissionCodes()) {
            PermissionPo permission = resolvePermission(permissionCode, permissionsByCode);
            if (grantsByPermissionId.containsKey(permission.getId())) {
                continue;
            }
            RolePermissionPo grant = new RolePermissionPo();
            grant.setRoleId(role.getId());
            grant.setPermissionId(permission.getId());
            grant.setGrantedAt(Instant.now());
            rolePermissionRepository.save(grant);
            changed = true;
        }
        return changed;
    }

    private void validate(String ownerApp, List<RbacResourceSeed> resources, List<RbacRolePresetSeed> rolePresets) {
        Set<String> resourceCodes = new LinkedHashSet<>();
        Map<String, RbacResourceSeed> seedsByCode = new LinkedHashMap<>();
        for (RbacResourceSeed seed : resources) {
            validateResourceSeed(ownerApp, seed);
            if (!resourceCodes.add(seed.code())) {
                throw new IllegalStateException("duplicate rbac resource code: " + seed.code());
            }
            seedsByCode.put(seed.code(), seed);
        }
        validateReferences(seedsByCode);
        validateRolePresets(ownerApp, rolePresets, resourceCodes);
    }

    private void validateResourceSeed(String ownerApp, RbacResourceSeed seed) {
        requireText(seed.appCode(), "resource appCode");
        requireText(seed.code(), "resource code");
        requireText(seed.name(), "resource name");
        if (!ownerApp.equals(seed.appCode())) {
            throw new IllegalStateException("resource appCode does not match synchronization app: " + seed.code());
        }
        if (!isCodeInNamespace(seed.code(), seed.type(), ownerApp)) {
            throw new IllegalStateException("resource code is outside app namespace: " + seed.code());
        }
        if (seed.type() == PermissionType.API && seed.api() == null) {
            throw new IllegalStateException("api resource detail is required: " + seed.code());
        }
        if (seed.type() == PermissionType.MENU && seed.menu() == null) {
            throw new IllegalStateException("menu resource detail is required: " + seed.code());
        }
        if (seed.type() == PermissionType.BUTTON && seed.button() == null) {
            throw new IllegalStateException("button resource detail is required: " + seed.code());
        }
        if (seed.type() == PermissionType.API && seed.api().publicFlag()
                && !RbacPublicApiPolicy.isAllowedPublicRule(seed.api().httpMethod(), seed.api().urlPattern())) {
            throw new IllegalStateException("public api resource is not allowed by policy: " + seed.code());
        }
    }

    private void validateReferences(Map<String, RbacResourceSeed> seedsByCode) {
        for (RbacResourceSeed seed : seedsByCode.values()) {
            if (StringUtils.hasText(seed.parentCode())) {
                PermissionType requiredParentType = seed.type() == PermissionType.BUTTON ? PermissionType.MENU : PermissionType.MENU;
                validatePermissionReference(seed.parentCode(), requiredParentType, seedsByCode);
            }
            if (seed.type() == PermissionType.BUTTON) {
                for (String apiCode : seed.buttonApiCodes()) {
                    validatePermissionReference(apiCode, PermissionType.API, seedsByCode);
                }
            }
        }
        validateApiRouteUniqueness(seedsByCode.values().stream()
                .filter(seed -> seed.type() == PermissionType.API)
                .toList());
    }

    private void validatePermissionReference(String code, PermissionType expectedType, Map<String, RbacResourceSeed> seedsByCode) {
        RbacResourceSeed localSeed = seedsByCode.get(code);
        if (localSeed != null) {
            if (localSeed.type() != expectedType) {
                throw new IllegalStateException("rbac resource reference has wrong type: " + code);
            }
            return;
        }
        PermissionPo permission = permissionRepository.findByPermCodeAndDeletedFalse(code)
                .orElseThrow(() -> new IllegalStateException("rbac resource reference does not exist: " + code));
        if (permission.getPermType() != expectedType) {
            throw new IllegalStateException("rbac resource reference has wrong type: " + code);
        }
    }

    private void validateApiRouteUniqueness(List<RbacResourceSeed> apiSeeds) {
        Set<String> routeKeys = new LinkedHashSet<>();
        for (RbacResourceSeed seed : apiSeeds) {
            String routeKey = seed.api().httpMethod() + " " + seed.api().urlPattern() + " " + seed.api().matcherType();
            if (!routeKeys.add(routeKey)) {
                throw new IllegalStateException("duplicate rbac api route declaration: " + routeKey);
            }
        }
    }

    private void validateRolePresets(String ownerApp, List<RbacRolePresetSeed> rolePresets, Set<String> resourceCodes) {
        for (RbacRolePresetSeed seed : rolePresets) {
            requireText(seed.appCode(), "role preset appCode");
            requireText(seed.roleCode(), "role preset code");
            requireText(seed.roleName(), "role preset name");
            if (!ownerApp.equals(seed.appCode())) {
                throw new IllegalStateException("role preset appCode does not match synchronization app: " + seed.roleCode());
            }
            if (SUPER_ADMIN_ROLE_CODE.equals(seed.roleCode())) {
                throw new IllegalStateException("business resource providers cannot declare SUPER_ADMIN");
            }
            for (String permissionCode : seed.permissionCodes()) {
                if (resourceCodes.contains(permissionCode)) {
                    continue;
                }
                permissionRepository.findByPermCodeAndDeletedFalse(permissionCode)
                        .orElseThrow(() -> new IllegalStateException("role preset permission does not exist: " + permissionCode));
            }
        }
    }

    private PermissionPo resolvePermission(String code, Map<String, PermissionPo> permissionsByCode) {
        PermissionPo permission = permissionsByCode.get(code);
        if (permission != null) {
            return permission;
        }
        permission = permissionRepository.findByPermCodeAndDeletedFalse(code)
                .orElseThrow(() -> new IllegalStateException("rbac permission does not exist: " + code));
        permissionsByCode.put(code, permission);
        return permission;
    }

    private int menuDepth(RbacResourceSeed seed, Map<String, RbacResourceSeed> seedsByCode) {
        int depth = 0;
        String parentCode = seed.parentCode();
        Set<String> visited = new LinkedHashSet<>();
        while (StringUtils.hasText(parentCode)) {
            if (!visited.add(parentCode)) {
                throw new IllegalStateException("rbac menu parent cycle detected: " + seed.code());
            }
            RbacResourceSeed parent = seedsByCode.get(parentCode);
            if (parent == null) {
                break;
            }
            depth++;
            parentCode = parent.parentCode();
        }
        return depth;
    }

    private String syncHash(RbacResourceSeed seed) {
        String payload = String.join("|",
                seed.appCode(),
                nullToEmpty(seed.serviceTag()),
                seed.code(),
                seed.type().name(),
                seed.name(),
                nullToEmpty(seed.parentCode()),
                Objects.toString(seed.status(), ""),
                Integer.toString(seed.sortNo()),
                nullToEmpty(seed.description()),
                Objects.toString(seed.menu(), ""),
                Objects.toString(seed.button(), ""),
                Objects.toString(seed.api(), ""),
                String.join(",", seed.buttonApiCodes()),
                sourceName(seed.source()));
        return digest(payload);
    }

    private String syncHash(RbacRolePresetSeed seed) {
        String payload = String.join("|",
                seed.appCode(),
                seed.roleCode(),
                seed.roleName(),
                nullToEmpty(seed.description()),
                Integer.toString(seed.sortNo()),
                String.join(",", seed.permissionCodes()),
                sourceName(seed.source()));
        return digest(payload);
    }

    private String digest(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("rbac resource sync digest algorithm is unavailable", e);
        }
    }

    private String sourceKey(String ownerApp, String code) {
        return ownerApp + ":" + code;
    }

    private String sourceName(RbacResourceSource source) {
        return (source == null ? RbacResourceSource.PROVIDER : source).name();
    }

    private String codePrefix(PermissionType type, String ownerApp) {
        if (type == PermissionType.BUTTON) {
            return "btn:" + ownerApp + ":";
        }
        return type.name().toLowerCase() + ":" + ownerApp + ":";
    }

    private boolean isCodeInNamespace(String code, PermissionType type, String ownerApp) {
        if (type == PermissionType.MENU && ("menu:" + ownerApp).equals(code)) {
            return true;
        }
        return code.startsWith(codePrefix(type, ownerApp));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(fieldName + " is required");
        }
        return value;
    }

    private record PermissionSyncResult(PermissionPo permission, boolean changed, boolean writable) {
    }

}
