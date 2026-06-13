package top.egon.mario.rag.service.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
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
import top.egon.mario.rbac.repository.ApiRepository;
import top.egon.mario.rbac.repository.ButtonApiRepository;
import top.egon.mario.rbac.repository.ButtonRepository;
import top.egon.mario.rbac.repository.MenuRepository;
import top.egon.mario.rbac.repository.PermissionRepository;
import top.egon.mario.rbac.repository.RolePermissionRepository;
import top.egon.mario.rbac.repository.RoleRepository;
import top.egon.mario.rbac.service.model.RbacPermissionChangedEvent;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Seeds RAG menu, button and API permissions into the existing RBAC model.
 */
@Component
@ConditionalOnProperty(prefix = "mario.rag.rbac-bootstrap", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class RagRbacBootstrap implements ApplicationRunner {

    private static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";

    private final PermissionRepository permissionRepository;
    private final MenuRepository menuRepository;
    private final ButtonRepository buttonRepository;
    private final ApiRepository apiRepository;
    private final ButtonApiRepository buttonApiRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final RagPermissionCatalog catalog = new RagPermissionCatalog();

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        bootstrap();
    }

    /**
     * Writes RAG permissions and grants them to SUPER_ADMIN when the role exists.
     */
    @Transactional
    public void bootstrap() {
        catalog.apis().forEach(this::ensureApiPermission);
        catalog.menus().forEach(this::ensureMenuPermission);
        catalog.buttons().forEach(this::ensureButtonPermission);
        grantSuperAdmin(catalog.apis().stream().map(RagPermissionCatalog.ApiPermissionSeed::permCode).toList());
        grantSuperAdmin(catalog.menus().stream().map(RagPermissionCatalog.MenuPermissionSeed::permCode).toList());
        grantSuperAdmin(catalog.buttons().stream().map(RagPermissionCatalog.ButtonPermissionSeed::permCode).toList());
        eventPublisher.publishEvent(new RbacPermissionChangedEvent("bootstrap rag permissions"));
        LogUtil.info(log).log("rag rbac bootstrap completed");
    }

    private PermissionPo ensurePermission(String permCode, String permName, PermissionType type, Integer sortNo) {
        PermissionPo permission = permissionRepository.findByPermCodeAndDeletedFalse(permCode).orElseGet(PermissionPo::new);
        permission.setPermCode(permCode);
        permission.setPermName(permName);
        permission.setPermType(type);
        permission.setStatus(PermissionStatus.ENABLED);
        permission.setSortNo(sortNo == null ? 0 : sortNo);
        permission.setDeleted(false);
        return permissionRepository.save(permission);
    }

    private void ensureMenuPermission(RagPermissionCatalog.MenuPermissionSeed seed) {
        PermissionPo permission = ensurePermission(seed.permCode(), seed.permName(), PermissionType.MENU, seed.sortNo());
        if (seed.parentPermCode() != null) {
            permissionRepository.findByPermCodeAndDeletedFalse(seed.parentPermCode())
                    .ifPresent(parent -> permission.setParentId(parent.getId()));
            permissionRepository.save(permission);
        }
        MenuPo menu = menuRepository.findById(permission.getId()).orElseGet(MenuPo::new);
        menu.setPermissionId(permission.getId());
        menu.setParentMenuId(permission.getParentId());
        menu.setRouteName(seed.routeName());
        menu.setRoutePath(seed.routePath());
        menu.setIcon("DatabaseOutlined");
        menu.setHidden(false);
        menu.setCacheable(true);
        menuRepository.save(menu);
    }

    private void ensureButtonPermission(RagPermissionCatalog.ButtonPermissionSeed seed) {
        PermissionPo menuPermission = permissionRepository.findByPermCodeAndDeletedFalse(seed.menuPermCode()).orElseThrow();
        PermissionPo permission = ensurePermission(seed.permCode(), seed.permName(), PermissionType.BUTTON, seed.sortNo());
        permission.setParentId(menuPermission.getId());
        permissionRepository.save(permission);
        ButtonPo button = buttonRepository.findById(permission.getId()).orElseGet(ButtonPo::new);
        button.setPermissionId(permission.getId());
        button.setMenuPermissionId(menuPermission.getId());
        button.setButtonKey(seed.buttonKey());
        button.setFrontendAction(seed.buttonKey());
        buttonRepository.save(button);
        linkButtonApi(permission.getId(), seed.apiPermCode());
    }

    private void ensureApiPermission(RagPermissionCatalog.ApiPermissionSeed seed) {
        PermissionPo permission = ensurePermission(seed.permCode(), seed.permName(), PermissionType.API, 0);
        ApiPo api = apiRepository.findById(permission.getId()).orElseGet(ApiPo::new);
        api.setPermissionId(permission.getId());
        api.setHttpMethod(seed.httpMethod());
        api.setUrlPattern(seed.urlPattern());
        api.setMatcherType(seed.matcherType());
        api.setPublicFlag(false);
        api.setServiceTag("rag");
        api.setOperationName(seed.permName());
        api.setRiskLevel(seed.riskLevel());
        api.setLastScannedAt(Instant.now());
        apiRepository.save(api);
    }

    private void linkButtonApi(Long buttonPermissionId, String apiPermCode) {
        PermissionPo apiPermission = permissionRepository.findByPermCodeAndDeletedFalse(apiPermCode).orElseThrow();
        boolean exists = buttonApiRepository.findByButtonPermissionId(buttonPermissionId).stream()
                .anyMatch(link -> apiPermission.getId().equals(link.getApiPermissionId()));
        if (exists) {
            return;
        }
        ButtonApiPo link = new ButtonApiPo();
        link.setButtonPermissionId(buttonPermissionId);
        link.setApiPermissionId(apiPermission.getId());
        link.setRelationType(ButtonApiRelationType.CALLS);
        link.setCreatedAt(Instant.now());
        buttonApiRepository.save(link);
    }

    private void grantSuperAdmin(Collection<String> permissionCodes) {
        roleRepository.findByRoleCodeAndDeletedFalse(SUPER_ADMIN_ROLE_CODE).ifPresent(role -> {
            Map<Long, RolePermissionPo> grantsByPermissionId = rolePermissionRepository.findByRoleId(role.getId()).stream()
                    .collect(Collectors.toMap(RolePermissionPo::getPermissionId, Function.identity()));
            permissionCodes.stream()
                    .map(permissionRepository::findByPermCodeAndDeletedFalse)
                    .flatMap(java.util.Optional::stream)
                    .filter(permission -> !grantsByPermissionId.containsKey(permission.getId()))
                    .forEach(permission -> saveRolePermission(role, permission));
        });
    }

    private void saveRolePermission(RolePo role, PermissionPo permission) {
        RolePermissionPo rolePermission = new RolePermissionPo();
        rolePermission.setRoleId(role.getId());
        rolePermission.setPermissionId(permission.getId());
        rolePermission.setGrantedAt(Instant.now());
        rolePermissionRepository.save(rolePermission);
    }

}
