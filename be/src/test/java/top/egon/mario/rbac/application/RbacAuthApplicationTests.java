package top.egon.mario.rbac.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import top.egon.mario.rbac.dto.request.LoginRequest;
import top.egon.mario.rbac.dto.response.LoginResponse;
import top.egon.mario.rbac.po.PermissionPo;
import top.egon.mario.rbac.po.RefreshTokenPo;
import top.egon.mario.rbac.po.RolePermissionPo;
import top.egon.mario.rbac.po.RolePo;
import top.egon.mario.rbac.po.UserPo;
import top.egon.mario.rbac.po.UserRolePo;
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
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class RbacAuthApplicationTests {

    @Autowired
    private RbacAuthApplication authApplication;
    @Autowired
    private PasswordEncoder passwordEncoder;
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
    void loginRefreshAndAccessAuthenticationExposeApiAuthorities() {
        UserPo user = new UserPo();
        user.setUsername("mario");
        user.setNickname("Mario");
        user.setPasswordHash(passwordEncoder.encode("secret"));
        user.setStatus(RbacStatus.ENABLED);
        user = userRepository.save(user);

        RolePo role = new RolePo();
        role.setRoleCode("ROLE_ADMIN");
        role.setRoleName("Administrator");
        role.setStatus(RbacStatus.ENABLED);
        role = roleRepository.save(role);

        PermissionPo apiPermission = new PermissionPo();
        apiPermission.setPermCode("api:rbac:user:list");
        apiPermission.setPermName("User list");
        apiPermission.setPermType(PermissionType.API);
        apiPermission.setStatus(PermissionStatus.ENABLED);
        apiPermission = permissionRepository.save(apiPermission);

        UserRolePo userRole = new UserRolePo();
        userRole.setUserId(user.getId());
        userRole.setRoleId(role.getId());
        userRole.setGrantedAt(Instant.now());
        userRoleRepository.save(userRole);

        RolePermissionPo rolePermission = new RolePermissionPo();
        rolePermission.setRoleId(role.getId());
        rolePermission.setPermissionId(apiPermission.getId());
        rolePermission.setGrantedAt(Instant.now());
        rolePermissionRepository.save(rolePermission);

        LoginResponse login = authApplication.login(new LoginRequest("mario", "secret"), "127.0.0.1", "test");

        assertThat(login.accessToken()).isNotBlank();
        assertThat(login.refreshToken()).isNotBlank();
        assertThat(login.roleCodes()).contains("ROLE_ADMIN");
        assertThat(login.permissionCodes()).contains("api:rbac:user:list");
        assertThat(login.permissionVersion()).isNotBlank();

        Authentication authentication = authApplication.authenticateAccessToken(login.accessToken());
        RbacPrincipal principal = (RbacPrincipal) authentication.getPrincipal();
        assertThat(principal.permissionVersion()).isEqualTo(login.permissionVersion());
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .contains("ROLE_ADMIN", "api:rbac:user:list");

        LoginResponse refreshed = authApplication.refresh(login.refreshToken(), "127.0.0.1", "test");
        assertThat(refreshed.refreshToken()).isNotEqualTo(login.refreshToken());
        assertThat(refreshed.permissionVersion()).isEqualTo(login.permissionVersion());
        RefreshTokenPo oldToken = refreshTokenRepository.findAll().stream()
                .filter(token -> token.getReplacedByTokenId() != null)
                .findFirst()
                .orElseThrow();
        assertThat(oldToken.getRevokedAt()).isNotNull();
    }

    @Test
    void permissionVersionChangesWhenUserRoleSnapshotChanges() {
        UserPo user = new UserPo();
        user.setUsername("luigi");
        user.setNickname("Luigi");
        user.setPasswordHash(passwordEncoder.encode("secret"));
        user.setStatus(RbacStatus.ENABLED);
        user = userRepository.save(user);

        RolePo role = new RolePo();
        role.setRoleCode("ROLE_VIEWER");
        role.setRoleName("Viewer");
        role.setStatus(RbacStatus.ENABLED);
        role = roleRepository.save(role);

        LoginResponse beforeGrant = authApplication.login(new LoginRequest("luigi", "secret"), "127.0.0.1", "test");

        UserRolePo userRole = new UserRolePo();
        userRole.setUserId(user.getId());
        userRole.setRoleId(role.getId());
        userRole.setGrantedAt(Instant.now());
        userRoleRepository.save(userRole);

        LoginResponse afterGrant = authApplication.currentUser(user.getId());

        assertThat(afterGrant.permissionVersion()).isNotEqualTo(beforeGrant.permissionVersion());
    }

}
