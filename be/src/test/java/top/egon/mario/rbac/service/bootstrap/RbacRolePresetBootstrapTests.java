package top.egon.mario.rbac.service.bootstrap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import top.egon.mario.rag.repository.RagKnowledgeBaseUserRepository;
import top.egon.mario.rbac.po.PermissionPo;
import top.egon.mario.rbac.po.RolePo;
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
import top.egon.mario.rbac.service.RbacPermissionVersionService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies preset roles are seeded without overwriting existing role grants.
 */
@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-api-key",
        "mario.rbac.resource-sync.enabled=false",
        "mario.rbac.role-presets.enabled=true",
        "mario.rbac.role-presets.sync-mode=CREATE_ONLY"
})
class RbacRolePresetBootstrapTests {

    @Autowired
    private RbacRolePresetBootstrap rolePresetBootstrap;
    @Autowired
    private PermissionRepository permissionRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private RolePermissionRepository rolePermissionRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private RoleInheritanceRepository roleInheritanceRepository;
    @Autowired
    private ButtonApiRepository buttonApiRepository;
    @Autowired
    private ApiRepository apiRepository;
    @Autowired
    private ButtonRepository buttonRepository;
    @Autowired
    private MenuRepository menuRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RbacPermissionVersionService permissionVersionService;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
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
    void bootstrapCreatesPresetRolesAndGrantsExistingPermissions() {
        PermissionPo chatPermission = permissionRepository.save(permission("api:chat:stream"));
        PermissionPo chatMenuPermission = permissionRepository.save(menuPermission("menu:chat"));
        PermissionPo rbacPermission = permissionRepository.save(permission("api:rbac:admin:*"));
        PermissionPo authSelfPermission = permissionRepository.save(permission("api:rbac:auth:self"));
        PermissionPo meSelfPermission = permissionRepository.save(permission("api:rbac:me:self"));
        PermissionPo dashboardMenuPermission = permissionRepository.save(menuPermission("menu:agent"));
        PermissionPo dashboardSelfPermission = permissionRepository.save(permission("api:agent:model-audit:dashboard:self"));
        PermissionPo dashboardGlobalPermission = permissionRepository.save(permission("api:agent:model-audit:dashboard:global"));
        PermissionPo dashboardUserOptionsPermission = permissionRepository.save(permission("api:agent:model-audit:dashboard:user-options"));

        rolePresetBootstrap.bootstrap();

        RolePo chatRole = roleRepository.findByRoleCodeAndDeletedFalse("CHAT_USER").orElseThrow();
        RolePo rbacRole = roleRepository.findByRoleCodeAndDeletedFalse("RBAC_ADMIN").orElseThrow();
        assertThat(chatRole.isBuiltIn()).isFalse();
        assertThat(chatRole.isManaged()).isTrue();
        assertThat(chatRole.getOwnerApp()).isEqualTo("rbac");
        assertThat(chatRole.getStatus()).isEqualTo(RbacStatus.ENABLED);
        assertThat(rolePermissionRepository.findByRoleId(chatRole.getId()))
                .extracting("permissionId")
                .contains(chatPermission.getId(), chatMenuPermission.getId(),
                        authSelfPermission.getId(), meSelfPermission.getId(),
                        dashboardMenuPermission.getId(), dashboardSelfPermission.getId());
        assertThat(rbacRole.isBuiltIn()).isFalse();
        assertThat(rbacRole.isManaged()).isTrue();
        assertThat(rbacRole.getOwnerApp()).isEqualTo("rbac");
        assertThat(rbacRole.getStatus()).isEqualTo(RbacStatus.ENABLED);
        assertThat(rolePermissionRepository.findByRoleId(rbacRole.getId()))
                .extracting("permissionId")
                .contains(rbacPermission.getId(), authSelfPermission.getId(), meSelfPermission.getId(),
                        dashboardMenuPermission.getId(), dashboardSelfPermission.getId(),
                        dashboardGlobalPermission.getId(), dashboardUserOptionsPermission.getId());
        assertThat(chatRole.getPermissionVersion()).isGreaterThan(0);
    }

    @Test
    void createOnlyModeDoesNotGrantExistingPresetRoles() {
        permissionRepository.save(permission("api:chat:stream"));
        RolePo existingRole = roleRepository.save(role("CHAT_USER"));

        rolePresetBootstrap.bootstrap();

        assertThat(rolePermissionRepository.findByRoleId(existingRole.getId())).isEmpty();
    }

    @Test
    void forceSyncDoesNotGrantExistingOrdinaryRoleWithPresetCode() {
        permissionRepository.save(permission("api:chat:stream"));
        RolePo existingRole = roleRepository.save(role("CHAT_USER"));
        RbacRolePresetBootstrap forceSyncBootstrap = new RbacRolePresetBootstrap(
                new RbacRolePresetBootstrapProperties(true, RbacRolePresetBootstrapProperties.SyncMode.FORCE_SYNC),
                roleRepository,
                permissionRepository,
                rolePermissionRepository,
                permissionVersionService,
                eventPublisher
        );

        forceSyncBootstrap.bootstrap();

        RolePo unchangedRole = roleRepository.findById(existingRole.getId()).orElseThrow();
        assertThat(unchangedRole.isManaged()).isFalse();
        assertThat(rolePermissionRepository.findByRoleId(existingRole.getId())).isEmpty();
    }

    private RolePo role(String code) {
        RolePo role = new RolePo();
        role.setRoleCode(code);
        role.setRoleName(code);
        role.setStatus(RbacStatus.ENABLED);
        return role;
    }

    private PermissionPo permission(String code) {
        PermissionPo permission = new PermissionPo();
        permission.setPermCode(code);
        permission.setPermName(code);
        permission.setPermType(PermissionType.API);
        permission.setStatus(PermissionStatus.ENABLED);
        return permission;
    }

    private PermissionPo menuPermission(String code) {
        PermissionPo permission = permission(code);
        permission.setPermType(PermissionType.MENU);
        return permission;
    }

}
