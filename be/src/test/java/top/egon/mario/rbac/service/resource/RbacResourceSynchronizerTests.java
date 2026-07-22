package top.egon.mario.rbac.service.resource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.rag.repository.RagKnowledgeBaseUserRepository;
import top.egon.mario.rbac.po.ApiPo;
import top.egon.mario.rbac.po.ButtonApiPo;
import top.egon.mario.rbac.po.ButtonPo;
import top.egon.mario.rbac.po.MenuPo;
import top.egon.mario.rbac.po.PermissionPo;
import top.egon.mario.rbac.po.RolePermissionPo;
import top.egon.mario.rbac.po.RolePo;
import top.egon.mario.rbac.po.enums.ApiMatcherType;
import top.egon.mario.rbac.po.enums.ApiRiskLevel;
import top.egon.mario.rbac.po.enums.PermissionStatus;
import top.egon.mario.rbac.po.enums.PermissionType;
import top.egon.mario.rbac.po.enums.RbacStatus;
import top.egon.mario.rbac.repository.ApiRepository;
import top.egon.mario.rbac.repository.AuditLogRepository;
import top.egon.mario.rbac.repository.ButtonApiRepository;
import top.egon.mario.rbac.repository.ButtonRepository;
import top.egon.mario.rbac.repository.MenuRepository;
import top.egon.mario.rbac.repository.PermissionRepository;
import top.egon.mario.rbac.repository.RefreshTokenRepository;
import top.egon.mario.rbac.repository.RoleInheritanceRepository;
import top.egon.mario.rbac.repository.RolePermissionRepository;
import top.egon.mario.rbac.repository.RoleRepository;
import top.egon.mario.rbac.repository.OneTimeTokenRepository;
import top.egon.mario.rbac.repository.UserRepository;
import top.egon.mario.rbac.repository.UserRoleRepository;
import top.egon.mario.rbac.service.resource.model.RbacApiSeed;
import top.egon.mario.rbac.service.resource.model.RbacButtonSeed;
import top.egon.mario.rbac.service.resource.model.RbacMenuSeed;
import top.egon.mario.rbac.service.resource.model.RbacResourceSeed;
import top.egon.mario.rbac.service.resource.model.RbacResourceSource;
import top.egon.mario.rbac.service.resource.model.RbacRolePresetSeed;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies RBAC resources are synchronized from declarations without mutating manual role grants.
 */
@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-api-key",
        "mario.rbac.resource-sync.enabled=false"
})
class RbacResourceSynchronizerTests {

    @Autowired
    private RbacResourceSynchronizer synchronizer;
    @Autowired
    private PermissionRepository permissionRepository;
    @Autowired
    private MenuRepository menuRepository;
    @Autowired
    private ButtonRepository buttonRepository;
    @Autowired
    private ApiRepository apiRepository;
    @Autowired
    private ButtonApiRepository buttonApiRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private RolePermissionRepository rolePermissionRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private RoleInheritanceRepository roleInheritanceRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OneTimeTokenRepository oneTimeTokenRepository;
    @Autowired
    private RagKnowledgeBaseUserRepository knowledgeBaseUserRepository;

    @BeforeEach
    void setUp() {
        knowledgeBaseUserRepository.deleteAll();
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        rolePermissionRepository.deleteAll();
        userRoleRepository.deleteAll();
        roleInheritanceRepository.deleteAll();
        buttonApiRepository.deleteAll();
        apiRepository.deleteAll();
        buttonRepository.deleteAll();
        menuRepository.deleteAll();
        List<PermissionPo> permissions = permissionRepository.findAll();
        permissions.forEach(permission -> permission.setParentId(null));
        permissionRepository.saveAll(permissions);
        permissionRepository.flush();
        permissionRepository.deleteAll();
        roleRepository.deleteAll();
        oneTimeTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void synchronizeCreatesMenuButtonApiAndButtonApiLink() {
        synchronizer.synchronize("rag", List.of(apiSeed(), rootMenuSeed(), childMenuSeed(), buttonSeed()), List.of());

        PermissionPo apiPermission = permissionRepository.findByPermCodeAndDeletedFalse("api:rag:document:*").orElseThrow();
        PermissionPo rootMenuPermission = permissionRepository.findByPermCodeAndDeletedFalse("menu:rag").orElseThrow();
        PermissionPo menuPermission = permissionRepository.findByPermCodeAndDeletedFalse("menu:rag:documents").orElseThrow();
        PermissionPo buttonPermission = permissionRepository.findByPermCodeAndDeletedFalse("btn:rag:doc:upload").orElseThrow();

        assertThat(apiPermission.isManaged()).isTrue();
        assertThat(apiPermission.getOwnerApp()).isEqualTo("rag");
        assertThat(apiPermission.getSourceType()).isEqualTo(RbacResourceSource.PROVIDER.name());
        assertThat(apiPermission.getSourceKey()).isEqualTo("rag:api:rag:document:*");
        assertThat(apiPermission.getSyncHash()).isNotBlank();
        assertThat(apiPermission.getLastSyncedAt()).isNotNull();
        assertThat(apiPermission.getLastSeenAt()).isNotNull();
        assertThat(menuPermission.getParentId()).isEqualTo(rootMenuPermission.getId());
        assertThat(buttonPermission.getParentId()).isEqualTo(menuPermission.getId());

        ApiPo api = apiRepository.findById(apiPermission.getId()).orElseThrow();
        assertThat(api.getHttpMethod()).isEqualTo("ANY");
        assertThat(api.getUrlPattern()).isEqualTo("/api/rag/documents/**");
        assertThat(api.getMatcherType()).isEqualTo(ApiMatcherType.ANT);
        assertThat(api.getServiceTag()).isEqualTo("rag");
        assertThat(api.getRiskLevel()).isEqualTo(ApiRiskLevel.HIGH);

        MenuPo menu = menuRepository.findById(menuPermission.getId()).orElseThrow();
        assertThat(menu.getParentMenuId()).isEqualTo(rootMenuPermission.getId());
        assertThat(menu.getRouteName()).isEqualTo("rag-documents");
        assertThat(menu.getRoutePath()).isEqualTo("/rag/documents");

        ButtonPo button = buttonRepository.findById(buttonPermission.getId()).orElseThrow();
        assertThat(button.getMenuPermissionId()).isEqualTo(menuPermission.getId());
        assertThat(button.getButtonKey()).isEqualTo("upload");
        assertThat(button.getFrontendAction()).isEqualTo("upload");

        assertThat(buttonApiRepository.findByButtonPermissionId(buttonPermission.getId()))
                .extracting(ButtonApiPo::getApiPermissionId)
                .containsExactly(apiPermission.getId());
    }

    @Test
    void synchronizeUpdatesManagedResourceFields() {
        synchronizer.synchronize("rag", List.of(apiSeed(), rootMenuSeed(), childMenuSeed(), buttonSeed()), List.of());
        PermissionPo apiPermission = permissionRepository.findByPermCodeAndDeletedFalse("api:rag:document:*").orElseThrow();
        String originalHash = apiPermission.getSyncHash();

        RbacResourceSeed updatedApi = RbacResourceSeed.api(
                "rag",
                "rag",
                "api:rag:document:*",
                "RAG 文档管理新版",
                PermissionStatus.ENABLED,
                0,
                "updated",
                new RbacApiSeed("POST", "/api/rag/documents/**", ApiMatcherType.ANT, false, ApiRiskLevel.MEDIUM),
                RbacResourceSource.PROVIDER
        );

        synchronizer.synchronize("rag", List.of(updatedApi, rootMenuSeed(), childMenuSeed(), buttonSeed()), List.of());

        PermissionPo updatedPermission = permissionRepository.findByPermCodeAndDeletedFalse("api:rag:document:*").orElseThrow();
        ApiPo updatedDetail = apiRepository.findById(updatedPermission.getId()).orElseThrow();
        assertThat(updatedPermission.getPermName()).isEqualTo("RAG 文档管理新版");
        assertThat(updatedPermission.getDescription()).isEqualTo("updated");
        assertThat(updatedPermission.getSyncHash()).isNotEqualTo(originalHash);
        assertThat(updatedDetail.getHttpMethod()).isEqualTo("POST");
        assertThat(updatedDetail.getRiskLevel()).isEqualTo(ApiRiskLevel.MEDIUM);
    }

    @Test
    void synchronizeDoesNotClaimManualResourceWithSameCode() {
        PermissionPo manualApi = manualApiPermission("api:rag:document:*");
        manualApi.setPermName("Manual RAG API");
        manualApi.setDescription("Manually maintained API permission.");
        manualApi = permissionRepository.save(manualApi);

        synchronizer.synchronize("rag", List.of(apiSeed(), rootMenuSeed(), childMenuSeed(), buttonSeed()), List.of());

        PermissionPo unchangedApi = permissionRepository.findById(manualApi.getId()).orElseThrow();
        PermissionPo buttonPermission = permissionRepository.findByPermCodeAndDeletedFalse("btn:rag:doc:upload").orElseThrow();
        assertThat(unchangedApi.isManaged()).isFalse();
        assertThat(unchangedApi.getPermName()).isEqualTo("Manual RAG API");
        assertThat(unchangedApi.getDescription()).isEqualTo("Manually maintained API permission.");
        assertThat(unchangedApi.getOwnerApp()).isNull();
        assertThat(apiRepository.findById(manualApi.getId())).isEmpty();
        assertThat(buttonApiRepository.findByButtonPermissionId(buttonPermission.getId()))
                .extracting(ButtonApiPo::getApiPermissionId)
                .containsExactly(manualApi.getId());
    }

    @Test
    void synchronizeDoesNotGrantOrdinaryRoleWhenRolePresetHasSameCode() {
        PermissionPo existingApi = permissionRepository.save(manualApiPermission("api:rag:document:*"));
        RolePo ordinaryRole = roleRepository.save(role("RAG_OPERATOR", false));
        RolePermissionPo existingGrant = new RolePermissionPo();
        existingGrant.setRoleId(ordinaryRole.getId());
        existingGrant.setPermissionId(existingApi.getId());
        existingGrant.setGrantedAt(java.time.Instant.now());
        rolePermissionRepository.save(existingGrant);

        synchronizer.synchronize("rag", List.of(apiSeed(), rootMenuSeed(), childMenuSeed(), buttonSeed()),
                List.of(new RbacRolePresetSeed(
                        "rag",
                        "RAG_OPERATOR",
                        "RAG Operator",
                        "System role for operating RAG resources.",
                        30,
                        List.of("menu:rag", "menu:rag:documents", "btn:rag:doc:upload", "api:rag:document:*"),
                        RbacResourceSource.PROVIDER
                )));

        RolePo unchangedRole = roleRepository.findByRoleCodeAndDeletedFalse("RAG_OPERATOR").orElseThrow();
        assertThat(unchangedRole.isManaged()).isFalse();
        assertThat(rolePermissionRepository.findByRoleId(ordinaryRole.getId()))
                .extracting(RolePermissionPo::getPermissionId)
                .containsExactly(existingApi.getId());
    }

    @Test
    void synchronizeAppendsMissingGrantsOnlyForManagedPresetRoles() {
        RolePo managedRole = roleRepository.save(role("RAG_OPERATOR", true));

        synchronizer.synchronize("rag", List.of(apiSeed(), rootMenuSeed(), childMenuSeed(), buttonSeed()),
                List.of(new RbacRolePresetSeed(
                        "rag",
                        "RAG_OPERATOR",
                        "RAG Operator",
                        "System role for operating RAG resources.",
                        30,
                        List.of("menu:rag", "menu:rag:documents", "btn:rag:doc:upload", "api:rag:document:*"),
                        RbacResourceSource.PROVIDER
                )));

        List<Long> permissionIds = List.of(
                permissionRepository.findByPermCodeAndDeletedFalse("api:rag:document:*").orElseThrow().getId(),
                permissionRepository.findByPermCodeAndDeletedFalse("menu:rag").orElseThrow().getId(),
                permissionRepository.findByPermCodeAndDeletedFalse("menu:rag:documents").orElseThrow().getId(),
                permissionRepository.findByPermCodeAndDeletedFalse("btn:rag:doc:upload").orElseThrow().getId()
        );
        RolePo updatedRole = roleRepository.findById(managedRole.getId()).orElseThrow();
        assertThat(updatedRole.isManaged()).isTrue();
        assertThat(updatedRole.getPermissionVersion()).isGreaterThan(0);
        assertThat(rolePermissionRepository.findByRoleId(managedRole.getId()))
                .extracting(RolePermissionPo::getPermissionId)
                .containsExactlyInAnyOrderElementsOf(permissionIds);
    }

    @Test
    void synchronizeDoesNotBumpRoleVersionWhenManagedResourcesAreUnchanged() {
        RolePo managedRole = roleRepository.save(role("RAG_OPERATOR", true));
        List<RbacResourceSeed> resources = List.of(apiSeed(), rootMenuSeed(), childMenuSeed(), buttonSeed());
        List<RbacRolePresetSeed> rolePresets = List.of(new RbacRolePresetSeed(
                "rag",
                "RAG_OPERATOR",
                "RAG Operator",
                "System role for operating RAG resources.",
                30,
                List.of("menu:rag", "menu:rag:documents", "btn:rag:doc:upload", "api:rag:document:*"),
                RbacResourceSource.PROVIDER
        ));
        synchronizer.synchronize("rag", resources, rolePresets);
        long permissionVersion = roleRepository.findById(managedRole.getId()).orElseThrow().getPermissionVersion();

        synchronizer.synchronize("rag", resources, rolePresets);

        assertThat(roleRepository.findById(managedRole.getId()).orElseThrow().getPermissionVersion())
                .isEqualTo(permissionVersion);
    }

    @Test
    void synchronizeRolePresetMayReferencePreviouslySyncedResourceFromAnotherApp() {
        synchronizer.synchronize("rbac", List.of(authSelfApiSeed()), List.of());
        RolePo managedRole = roleRepository.save(role("RAG_OPERATOR", true));

        synchronizer.synchronize("rag", List.of(apiSeed(), rootMenuSeed(), childMenuSeed(), buttonSeed()),
                List.of(new RbacRolePresetSeed(
                        "rag",
                        "RAG_OPERATOR",
                        "RAG Operator",
                        "System role for operating RAG resources.",
                        30,
                        List.of("api:rag:document:*", "api:rbac:auth:self"),
                        RbacResourceSource.PROVIDER
                )));

        PermissionPo ragApi = permissionRepository.findByPermCodeAndDeletedFalse("api:rag:document:*").orElseThrow();
        PermissionPo authSelfApi = permissionRepository.findByPermCodeAndDeletedFalse("api:rbac:auth:self").orElseThrow();
        assertThat(rolePermissionRepository.findByRoleId(managedRole.getId()))
                .extracting(RolePermissionPo::getPermissionId)
                .contains(ragApi.getId(), authSelfApi.getId());
    }

    @Test
    void synchronizeDashboardResourcesCanBeGrantedThroughManagedAdminRoles() {
        RolePo rbacAdminRole = roleRepository.save(role("RBAC_ADMIN", true));
        synchronizer.synchronize("agent", List.of(
                dashboardMenuSeed(),
                dashboardApiSeed("api:agent:model-audit:dashboard:self",
                        "/api/agent/model-audit/dashboard/self/**",
                        ApiMatcherType.ANT),
                dashboardApiSeed("api:agent:model-audit:dashboard:global",
                        "/api/agent/model-audit/dashboard/global/**",
                        ApiMatcherType.ANT),
                dashboardApiSeed("api:agent:model-audit:dashboard:user-options",
                        "/api/agent/model-audit/dashboard/user-options",
                        ApiMatcherType.EXACT)
        ), List.of());

        synchronizer.synchronize("agent", List.of(), List.of(
                new RbacRolePresetSeed(
                        "agent",
                        "RBAC_ADMIN",
                        "RBAC Administrator",
                        "System role for RBAC management.",
                        10,
                        List.of("menu:agent", "api:agent:model-audit:dashboard:self",
                                "api:agent:model-audit:dashboard:global",
                                "api:agent:model-audit:dashboard:user-options"),
                        RbacResourceSource.PROVIDER
                )
        ));

        PermissionPo dashboardMenu = permissionRepository.findByPermCodeAndDeletedFalse("menu:agent").orElseThrow();
        PermissionPo dashboardSelf = permissionRepository.findByPermCodeAndDeletedFalse("api:agent:model-audit:dashboard:self").orElseThrow();
        PermissionPo dashboardGlobal = permissionRepository.findByPermCodeAndDeletedFalse("api:agent:model-audit:dashboard:global").orElseThrow();
        PermissionPo dashboardUserOptions = permissionRepository.findByPermCodeAndDeletedFalse("api:agent:model-audit:dashboard:user-options").orElseThrow();
        assertThat(rolePermissionRepository.findByRoleId(rbacAdminRole.getId()))
                .extracting(RolePermissionPo::getPermissionId)
                .containsExactlyInAnyOrder(dashboardMenu.getId(), dashboardSelf.getId(),
                        dashboardGlobal.getId(), dashboardUserOptions.getId());
    }

    private RbacResourceSeed rootMenuSeed() {
        return RbacResourceSeed.menu(
                "rag",
                "rag",
                "menu:rag",
                "RAG 管理",
                null,
                PermissionStatus.ENABLED,
                20,
                null,
                new RbacMenuSeed("rag", null, null, null, "DatabaseOutlined", false, true, null),
                RbacResourceSource.PROVIDER
        );
    }

    private RbacResourceSeed childMenuSeed() {
        return RbacResourceSeed.menu(
                "rag",
                "rag",
                "menu:rag:documents",
                "文档管理",
                "menu:rag",
                PermissionStatus.ENABLED,
                23,
                null,
                new RbacMenuSeed("rag-documents", "/rag/documents", null, null, "DatabaseOutlined", false, true, null),
                RbacResourceSource.PROVIDER
        );
    }

    private RbacResourceSeed buttonSeed() {
        return RbacResourceSeed.button(
                "rag",
                "rag",
                "btn:rag:doc:upload",
                "上传文档",
                "menu:rag:documents",
                PermissionStatus.ENABLED,
                1,
                null,
                new RbacButtonSeed("upload", "upload", null),
                List.of("api:rag:document:*"),
                RbacResourceSource.PROVIDER
        );
    }

    private RbacResourceSeed apiSeed() {
        return RbacResourceSeed.api(
                "rag",
                "rag",
                "api:rag:document:*",
                "RAG 文档管理",
                PermissionStatus.ENABLED,
                0,
                null,
                new RbacApiSeed("ANY", "/api/rag/documents/**", ApiMatcherType.ANT, false, ApiRiskLevel.HIGH),
                RbacResourceSource.PROVIDER
        );
    }

    private RbacResourceSeed authSelfApiSeed() {
        return RbacResourceSeed.api(
                "rbac",
                "rbac",
                "api:rbac:auth:self",
                "RBAC 认证自助接口",
                PermissionStatus.ENABLED,
                0,
                null,
                new RbacApiSeed("ANY", "/api/auth/**", ApiMatcherType.ANT, false, ApiRiskLevel.MEDIUM),
                RbacResourceSource.ANNOTATION
        );
    }

    private RbacResourceSeed dashboardMenuSeed() {
        return RbacResourceSeed.menu(
                "agent",
                "agent",
                "menu:agent",
                "首页控制台",
                null,
                PermissionStatus.ENABLED,
                10,
                null,
                new RbacMenuSeed("dashboard", "/dashboard", null, null, "DashboardOutlined", false, true, null),
                RbacResourceSource.PROVIDER
        );
    }

    private RbacResourceSeed dashboardApiSeed(String code, String pattern, ApiMatcherType matcherType) {
        return RbacResourceSeed.api(
                "agent",
                "agent",
                code,
                code,
                PermissionStatus.ENABLED,
                0,
                null,
                new RbacApiSeed("GET", pattern, matcherType, false, ApiRiskLevel.HIGH),
                RbacResourceSource.ANNOTATION
        );
    }

    private PermissionPo manualApiPermission(String code) {
        PermissionPo permission = new PermissionPo();
        permission.setPermCode(code);
        permission.setPermName(code);
        permission.setPermType(PermissionType.API);
        permission.setStatus(PermissionStatus.ENABLED);
        permission.setManaged(false);
        return permission;
    }

    private RolePo role(String code, boolean managed) {
        RolePo role = new RolePo();
        role.setRoleCode(code);
        role.setRoleName(code);
        role.setStatus(RbacStatus.ENABLED);
        role.setManaged(managed);
        role.setOwnerApp(managed ? "rag" : null);
        role.setSourceType(managed ? RbacResourceSource.PROVIDER.name() : null);
        role.setSourceKey(managed ? "rag:" + code : null);
        return role;
    }

}
