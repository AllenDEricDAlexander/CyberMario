package top.egon.mario.rbac.service.bootstrap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import top.egon.mario.rag.repository.RagKnowledgeBaseUserRepository;
import top.egon.mario.rbac.po.ApiPo;
import top.egon.mario.rbac.po.PermissionPo;
import top.egon.mario.rbac.po.RolePermissionPo;
import top.egon.mario.rbac.po.RolePo;
import top.egon.mario.rbac.po.UserPo;
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
import top.egon.mario.rbac.repository.UserRepository;
import top.egon.mario.rbac.repository.UserRoleRepository;
import top.egon.mario.rbac.service.resource.RbacResourceSynchronizer;
import top.egon.mario.rbac.service.resource.model.RbacApiSeed;
import top.egon.mario.rbac.service.resource.model.RbacMenuSeed;
import top.egon.mario.rbac.service.resource.model.RbacResourceSeed;
import top.egon.mario.rbac.service.resource.model.RbacResourceSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-api-key",
        "mario.rbac.bootstrap.admin.enabled=true",
        "mario.rbac.bootstrap.admin.username=admin",
        "mario.rbac.bootstrap.admin.password=Admin#2026Password!",
        "mario.rbac.bootstrap.admin.require-password-change=true"
})
class RbacAdminBootstrapTests {

    @Autowired
    private RbacAdminBootstrap adminBootstrap;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PermissionRepository permissionRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private RolePermissionRepository rolePermissionRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private RoleInheritanceRepository roleInheritanceRepository;
    @Autowired
    private ButtonApiRepository buttonApiRepository;
    @Autowired
    private ButtonRepository buttonRepository;
    @Autowired
    private MenuRepository menuRepository;
    @Autowired
    private ApiRepository apiRepository;
    @Autowired
    private RbacResourceSynchronizer synchronizer;
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
        userRepository.deleteAll();
    }

    @Test
    void bootstrapCreatesBuiltInSuperAdministrator() {
        adminBootstrap.bootstrap();

        UserPo admin = userRepository.findByUsernameAndDeletedFalse("admin").orElseThrow();
        assertThat(admin.getStatus()).isEqualTo(RbacStatus.ENABLED);
        assertThat(admin.isPasswordExpired()).isTrue();
        assertThat(admin.getPasswordHash()).startsWith("{argon2id}");
        assertThat(passwordEncoder.matches("Admin#2026Password!", admin.getPasswordHash())).isTrue();

        RolePo role = roleRepository.findByRoleCodeAndDeletedFalse("SUPER_ADMIN").orElseThrow();
        assertThat(role.isBuiltIn()).isTrue();
        assertThat(role.getStatus()).isEqualTo(RbacStatus.ENABLED);
        assertThat(userRoleRepository.findByUserId(admin.getId()))
                .extracting("roleId")
                .contains(role.getId());

        PermissionPo permission = permissionRepository.findByPermCodeAndDeletedFalse("api:rbac:admin:*").orElseThrow();
        PermissionPo chatPermission = permissionRepository.findByPermCodeAndDeletedFalse("api:chat:stream").orElseThrow();
        PermissionPo authSelfPermission = permissionRepository.findByPermCodeAndDeletedFalse("api:rbac:auth:self").orElseThrow();
        assertThat(permission.getPermType()).isEqualTo(PermissionType.API);
        assertThat(permission.getStatus()).isEqualTo(PermissionStatus.ENABLED);
        assertThat(chatPermission.getPermType()).isEqualTo(PermissionType.API);
        assertThat(chatPermission.getStatus()).isEqualTo(PermissionStatus.ENABLED);
        assertThat(authSelfPermission.getPermType()).isEqualTo(PermissionType.API);
        assertThat(authSelfPermission.getStatus()).isEqualTo(PermissionStatus.ENABLED);
        assertThat(rolePermissionRepository.findByRoleId(role.getId()))
                .extracting("permissionId")
                .contains(permission.getId(), chatPermission.getId(), authSelfPermission.getId());

        ApiPo api = apiRepository.findById(permission.getId()).orElseThrow();
        assertThat(api.getHttpMethod()).isEqualTo("ANY");
        assertThat(api.getUrlPattern()).isEqualTo("/api/admin/**");
        assertThat(api.getMatcherType()).isEqualTo(ApiMatcherType.ANT);
        assertThat(api.getRiskLevel()).isEqualTo(ApiRiskLevel.HIGH);

        ApiPo chatApi = apiRepository.findById(chatPermission.getId()).orElseThrow();
        assertThat(chatApi.getHttpMethod()).isEqualTo("POST");
        assertThat(chatApi.getUrlPattern()).isEqualTo("/demo/chat/stream");
        assertThat(chatApi.getMatcherType()).isEqualTo(ApiMatcherType.EXACT);
        assertThat(chatApi.getRiskLevel()).isEqualTo(ApiRiskLevel.MEDIUM);

        ApiPo authSelfApi = apiRepository.findById(authSelfPermission.getId()).orElseThrow();
        assertThat(authSelfApi.getHttpMethod()).isEqualTo("ANY");
        assertThat(authSelfApi.getUrlPattern()).isEqualTo("/api/auth/**");
        assertThat(authSelfApi.getMatcherType()).isEqualTo(ApiMatcherType.ANT);
        assertThat(authSelfApi.getRiskLevel()).isEqualTo(ApiRiskLevel.MEDIUM);
    }

    @Test
    void bootstrapIsIdempotentAndDoesNotOverwriteExistingAdminPassword() {
        adminBootstrap.bootstrap();
        String passwordHash = userRepository.findByUsernameAndDeletedFalse("admin").orElseThrow().getPasswordHash();

        adminBootstrap.bootstrap();

        UserPo admin = userRepository.findByUsernameAndDeletedFalse("admin").orElseThrow();
        RolePo role = roleRepository.findByRoleCodeAndDeletedFalse("SUPER_ADMIN").orElseThrow();
        PermissionPo permission = permissionRepository.findByPermCodeAndDeletedFalse("api:rbac:admin:*").orElseThrow();
        PermissionPo chatPermission = permissionRepository.findByPermCodeAndDeletedFalse("api:chat:stream").orElseThrow();
        assertThat(admin.getPasswordHash()).isEqualTo(passwordHash);
        assertThat(userRoleRepository.findByUserId(admin.getId())).hasSize(1);
        assertThat(rolePermissionRepository.findByRoleId(role.getId())).hasSize(3);
        assertThat(apiRepository.findById(permission.getId())).isPresent();
        assertThat(apiRepository.findById(chatPermission.getId())).isPresent();
    }

    @Test
    void bootstrapGrantsSuperAdminAllExistingPermissions() {
        PermissionPo ragPermission = permissionRepository.save(permission("api:rag:document:*", PermissionType.API));
        PermissionPo menuPermission = permissionRepository.save(permission("menu:rag", PermissionType.MENU));
        RolePo unrelatedRole = roleRepository.save(role("RAG_OPERATOR"));
        RolePermissionPo unrelatedGrant = new RolePermissionPo();
        unrelatedGrant.setRoleId(unrelatedRole.getId());
        unrelatedGrant.setPermissionId(ragPermission.getId());
        unrelatedGrant.setGrantedAt(Instant.now());
        rolePermissionRepository.save(unrelatedGrant);

        adminBootstrap.bootstrap();

        RolePo superAdmin = roleRepository.findByRoleCodeAndDeletedFalse("SUPER_ADMIN").orElseThrow();
        assertThat(rolePermissionRepository.findByRoleId(superAdmin.getId()))
                .extracting(RolePermissionPo::getPermissionId)
                .contains(ragPermission.getId(), menuPermission.getId());
        assertThat(rolePermissionRepository.findByRoleId(unrelatedRole.getId()))
                .extracting(RolePermissionPo::getPermissionId)
                .containsExactly(ragPermission.getId());
    }

    @Test
    void bootstrapGrantsSuperAdminAnnotationSynchronizedDashboardApis() {
        synchronizer.synchronize("agent", List.of(
                dashboardMenuSeed(),
                dashboardApiSeed("api:agent:model-audit:dashboard:self", "/api/agent/model-audit/dashboard/self"),
                dashboardApiSeed("api:agent:model-audit:dashboard:global", "/api/agent/model-audit/dashboard/global"),
                dashboardApiSeed("api:agent:model-audit:dashboard:user-options", "/api/agent/model-audit/dashboard/user-options")
        ), List.of());

        adminBootstrap.bootstrap();

        PermissionPo dashboardSelf = permissionRepository.findByPermCodeAndDeletedFalse("api:agent:model-audit:dashboard:self").orElseThrow();
        PermissionPo dashboardGlobal = permissionRepository.findByPermCodeAndDeletedFalse("api:agent:model-audit:dashboard:global").orElseThrow();
        PermissionPo dashboardUserOptions = permissionRepository.findByPermCodeAndDeletedFalse("api:agent:model-audit:dashboard:user-options").orElseThrow();
        PermissionPo dashboardMenu = permissionRepository.findByPermCodeAndDeletedFalse("menu:agent").orElseThrow();
        RolePo superAdmin = roleRepository.findByRoleCodeAndDeletedFalse("SUPER_ADMIN").orElseThrow();
        assertThat(rolePermissionRepository.findByRoleId(superAdmin.getId()))
                .extracting(RolePermissionPo::getPermissionId)
                .contains(dashboardSelf.getId(), dashboardGlobal.getId(), dashboardUserOptions.getId(),
                        dashboardMenu.getId());
        assertThat(apiRepository.findById(dashboardGlobal.getId()).orElseThrow().getUrlPattern())
                .isEqualTo("/api/agent/model-audit/dashboard/global");
    }

    @Test
    void bootstrapDoesNotOverwriteAnnotationManagedPermissionMetadata() {
        PermissionPo authSelfPermission = permission("api:rbac:auth:self", PermissionType.API);
        authSelfPermission.setManaged(true);
        authSelfPermission.setOwnerApp("rbac");
        authSelfPermission.setSourceType(RbacResourceSource.ANNOTATION.name());
        authSelfPermission.setSourceKey("rbac:api:rbac:auth:self");
        authSelfPermission.setSyncHash("annotation-sync-hash");
        permissionRepository.save(authSelfPermission);

        adminBootstrap.bootstrap();

        PermissionPo permission = permissionRepository.findByPermCodeAndDeletedFalse("api:rbac:auth:self").orElseThrow();
        assertThat(permission.getSourceType()).isEqualTo(RbacResourceSource.ANNOTATION.name());
        assertThat(permission.getSyncHash()).isEqualTo("annotation-sync-hash");
    }

    @Test
    void bootstrapDoesNotRequirePasswordWhenAdministratorAlreadyExists() {
        UserPo admin = new UserPo();
        admin.setUsername("admin");
        admin.setNickname("Administrator");
        admin.setPasswordHash(passwordEncoder.encode("Existing#2026Password!"));
        admin.setStatus(RbacStatus.ENABLED);
        admin = userRepository.save(admin);
        String passwordHash = admin.getPasswordHash();
        RbacAdminBootstrap noPasswordBootstrap = new RbacAdminBootstrap(
                new RbacAdminBootstrapProperties(true, "admin", "", true),
                userRepository,
                roleRepository,
                permissionRepository,
                apiRepository,
                userRoleRepository,
                rolePermissionRepository,
                passwordEncoder,
                eventPublisher
        );

        noPasswordBootstrap.bootstrap();

        RolePo role = roleRepository.findByRoleCodeAndDeletedFalse("SUPER_ADMIN").orElseThrow();
        assertThat(userRepository.findByUsernameAndDeletedFalse("admin").orElseThrow().getPasswordHash()).isEqualTo(passwordHash);
        assertThat(userRoleRepository.findByUserId(admin.getId()))
                .extracting("roleId")
                .contains(role.getId());
    }

    private PermissionPo permission(String code, PermissionType type) {
        PermissionPo permission = new PermissionPo();
        permission.setPermCode(code);
        permission.setPermName(code);
        permission.setPermType(type);
        permission.setStatus(PermissionStatus.ENABLED);
        return permission;
    }

    private RolePo role(String code) {
        RolePo role = new RolePo();
        role.setRoleCode(code);
        role.setRoleName(code);
        role.setStatus(RbacStatus.ENABLED);
        return role;
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

    private RbacResourceSeed dashboardApiSeed(String code, String pattern) {
        return RbacResourceSeed.api(
                "agent",
                "agent",
                code,
                code,
                PermissionStatus.ENABLED,
                0,
                null,
                new RbacApiSeed("GET", pattern, ApiMatcherType.EXACT, false, ApiRiskLevel.HIGH),
                RbacResourceSource.ANNOTATION
        );
    }

}
