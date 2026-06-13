package top.egon.mario.rbac.service.bootstrap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import top.egon.mario.rbac.po.ApiPo;
import top.egon.mario.rbac.po.PermissionPo;
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

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        rolePermissionRepository.deleteAll();
        userRoleRepository.deleteAll();
        roleInheritanceRepository.deleteAll();
        buttonApiRepository.deleteAll();
        apiRepository.deleteAll();
        buttonRepository.deleteAll();
        menuRepository.deleteAll();
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
        assertThat(permission.getPermType()).isEqualTo(PermissionType.API);
        assertThat(permission.getStatus()).isEqualTo(PermissionStatus.ENABLED);
        assertThat(chatPermission.getPermType()).isEqualTo(PermissionType.API);
        assertThat(chatPermission.getStatus()).isEqualTo(PermissionStatus.ENABLED);
        assertThat(rolePermissionRepository.findByRoleId(role.getId()))
                .extracting("permissionId")
                .contains(permission.getId(), chatPermission.getId());

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
        assertThat(rolePermissionRepository.findByRoleId(role.getId())).hasSize(2);
        assertThat(apiRepository.findById(permission.getId())).isPresent();
        assertThat(apiRepository.findById(chatPermission.getId())).isPresent();
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

}
