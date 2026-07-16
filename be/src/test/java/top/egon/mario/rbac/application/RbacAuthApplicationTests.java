package top.egon.mario.rbac.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import top.egon.mario.rag.repository.RagKnowledgeBaseUserRepository;
import top.egon.mario.rbac.dto.request.LoginRequest;
import top.egon.mario.rbac.dto.request.RegisterRequest;
import top.egon.mario.rbac.dto.response.LoginResponse;
import top.egon.mario.rbac.po.MenuPo;
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
import top.egon.mario.rbac.service.security.PasswordTransportEncryptionService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    @Autowired
    private RagKnowledgeBaseUserRepository knowledgeBaseUserRepository;
    @Autowired
    private PasswordTransportEncryptionService passwordTransportEncryptionService;

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
        permissionRepository.deleteAll();
        roleRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void registerCreatesEnabledUserWithDefaultBusinessRolesAndNoRbacAdminPermission() {
        RolePo chatRole = roleRepository.save(role("CHAT_BASIC"));
        RolePo ragRole = roleRepository.save(role("RAG_USER"));
        RolePo dashboardRole = roleRepository.save(role("AGENT_DASHBOARD_USER"));
        RolePo mcpRole = roleRepository.save(role("AGENT_MCP_USER"));
        RolePo imRole = roleRepository.save(role("IM_USER"));
        grant(chatRole, menuPermission("menu:chat", "chat", "/chat"));
        grant(chatRole, permission("api:chat:stream", PermissionType.API));
        grant(chatRole, permission("api:rbac:auth:self", PermissionType.API));
        grant(chatRole, permission("api:rbac:me:self", PermissionType.API));
        grant(ragRole, menuPermission("menu:rag", "rag", "/rag/chat"));
        grant(ragRole, permission("api:rag:document:*", PermissionType.API));
        grant(dashboardRole, menuPermission("menu:agent", "dashboard", "/dashboard"));
        grant(dashboardRole, permission("api:agent:model-audit:dashboard:self", PermissionType.API));
        grant(mcpRole, menuPermission("menu:agent:mcp-servers", "agent-mcp-servers", "/agent/mcp/servers"));
        grant(mcpRole, permission("btn:agent:mcp-server:add", PermissionType.BUTTON));
        grant(mcpRole, permission("btn:agent:mcp-server:edit", PermissionType.BUTTON));
        grant(mcpRole, permission("btn:agent:mcp-server:delete", PermissionType.BUTTON));
        grant(mcpRole, permission("btn:agent:mcp-server:test", PermissionType.BUTTON));
        grant(mcpRole, permission("btn:agent:mcp-server:discover", PermissionType.BUTTON));
        grant(mcpRole, permission("btn:agent:mcp-server:toggle", PermissionType.BUTTON));
        grant(mcpRole, permission("btn:agent:mcp-tool:edit-policy", PermissionType.BUTTON));
        grant(mcpRole, permission("btn:agent:mcp-tool:toggle", PermissionType.BUTTON));
        grant(mcpRole, permission("api:agent:mcp-server:collection", PermissionType.API));
        grant(mcpRole, permission("api:agent:mcp-server:*", PermissionType.API));
        grant(mcpRole, permission("api:agent:mcp-tool:collection", PermissionType.API));
        grant(mcpRole, permission("api:agent:mcp-tool:*", PermissionType.API));
        grant(imRole, menuPermission("menu:im", "im", "/im"));
        grant(imRole, permission("api:im:read", PermissionType.API));
        grant(imRole, permission("api:im:write", PermissionType.API));
        permission("menu:agent:mcp-logs", PermissionType.MENU);
        permission("btn:agent:mcp-log:view", PermissionType.BUTTON);
        permission("api:agent:mcp-log:collection", PermissionType.API);
        permission("api:agent:mcp-log:*", PermissionType.API);
        permission("api:agent:model-audit:dashboard:global", PermissionType.API);
        permission("api:agent:model-audit:dashboard:user-options", PermissionType.API);
        grant(roleRepository.save(role("RBAC_ADMIN")), permission("api:rbac:admin:*", PermissionType.API));

        RegisterRequest request = registerRequest("peach-001", "Peach", "password123", "Princess Peach",
                "peach@example.com", "13800000000", "https://example.com/avatar.png");

        LoginResponse response = authApplication.register(request, "127.0.0.1", "test");

        UserPo user = userRepository.findByAccountNoAndDeletedFalse("peach-001").orElseThrow();
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.user().getId()).isEqualTo(user.getId());
        assertThat(response.user().getAccountNo()).isEqualTo("peach-001");
        assertThat(response.user().getNickname()).isEqualTo("Princess Peach");
        assertThat(response.roleCodes()).containsExactlyInAnyOrder("CHAT_BASIC", "RAG_USER", "AGENT_DASHBOARD_USER",
                "AGENT_MCP_USER", "IM_USER");
        assertThat(response.menus()).extracting("permCode")
                .contains("menu:chat", "menu:rag", "menu:agent", "menu:agent:mcp-servers", "menu:im")
                .doesNotContain("menu:agent:mcp-tools", "menu:agent:mcp-logs");
        assertThat(response.buttonCodes())
                .contains("btn:agent:mcp-server:add",
                        "btn:agent:mcp-server:edit",
                        "btn:agent:mcp-server:delete",
                        "btn:agent:mcp-server:test",
                        "btn:agent:mcp-server:discover",
                        "btn:agent:mcp-server:toggle",
                        "btn:agent:mcp-tool:edit-policy",
                        "btn:agent:mcp-tool:toggle")
                .doesNotContain("btn:agent:mcp-log:view");
        assertThat(response.permissionCodes())
                .contains("api:chat:stream",
                        "api:rag:document:*",
                        "api:agent:model-audit:dashboard:self",
                        "api:agent:mcp-server:collection",
                        "api:agent:mcp-server:*",
                        "api:agent:mcp-tool:collection",
                        "api:agent:mcp-tool:*",
                        "api:im:read",
                        "api:im:write")
                .doesNotContain("api:rbac:admin:*",
                        "api:agent:model-audit:dashboard:global",
                        "api:agent:model-audit:dashboard:user-options",
                        "api:agent:mcp-log:collection",
                        "api:agent:mcp-log:*");
        assertThat(passwordEncoder.matches("password123", user.getPasswordHash())).isTrue();
        assertThat(user.getStatus()).isEqualTo(RbacStatus.ENABLED);
        assertThat(user.isLocked()).isFalse();
        assertThat(user.isPasswordExpired()).isFalse();
        assertThat(userRoleRepository.findByUserId(user.getId()))
                .extracting(UserRolePo::getRoleId)
                .containsExactlyInAnyOrder(chatRole.getId(), ragRole.getId(), dashboardRole.getId(), mcpRole.getId(),
                        imRole.getId());
    }

    @Test
    void registerFailsAtomicallyWhenImUserDefaultRoleIsMissing() {
        saveNonImDefaultRegisterRoles();
        RegisterRequest request = registerRequest("daisy-001", "Daisy", "password123", "Princess Daisy",
                null, null, null);

        assertThatThrownBy(() -> authApplication.register(request, "127.0.0.1", "test"))
                .extracting("code")
                .isEqualTo("RBAC_DEFAULT_ROLE_NOT_FOUND");
        assertThat(userRepository.findByAccountNoAndDeletedFalse("daisy-001")).isEmpty();
    }

    @Test
    void registerFailsAtomicallyWhenImUserDefaultRoleIsDisabled() {
        saveNonImDefaultRegisterRoles();
        RolePo imRole = role("IM_USER");
        imRole.setStatus(RbacStatus.DISABLED);
        roleRepository.save(imRole);
        RegisterRequest request = registerRequest("rosalina-001", "Rosalina", "password123", "Rosalina",
                null, null, null);

        assertThatThrownBy(() -> authApplication.register(request, "127.0.0.1", "test"))
                .extracting("code")
                .isEqualTo("RBAC_DEFAULT_ROLE_DISABLED");
        assertThat(userRepository.findByAccountNoAndDeletedFalse("rosalina-001")).isEmpty();
    }

    @Test
    void loginUsesAccountNoOrEmailAndRejectsUsername() {
        UserPo user = new UserPo();
        user.setAccountNo("mario-account");
        user.setUsername("mario-name");
        user.setEmail("mario@example.com");
        user.setNickname("Mario");
        user.setPasswordHash(passwordEncoder.encode("secret"));
        user.setStatus(RbacStatus.ENABLED);
        user = userRepository.save(user);

        LoginResponse accountLogin = authApplication.login(loginRequest("mario-account", "secret"), "127.0.0.1", "test");
        LoginResponse emailLogin = authApplication.login(loginRequest("mario@example.com", "secret"), "127.0.0.1", "test");

        assertThat(accountLogin.user().getId()).isEqualTo(user.getId());
        assertThat(emailLogin.user().getId()).isEqualTo(user.getId());
        assertThatThrownBy(() -> authApplication.login(loginRequest("mario-name", "secret"), "127.0.0.1", "test"))
                .hasMessageContaining("account or password is invalid");
    }

    @Test
    void loginRefreshAndAccessAuthenticationExposeApiAuthorities() {
        UserPo user = new UserPo();
        user.setAccountNo("mario");
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

        LoginResponse login = authApplication.login(loginRequest("mario", "secret"), "127.0.0.1", "test");

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
        user.setAccountNo("luigi");
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

        LoginResponse beforeGrant = authApplication.login(loginRequest("luigi", "secret"), "127.0.0.1", "test");

        UserRolePo userRole = new UserRolePo();
        userRole.setUserId(user.getId());
        userRole.setRoleId(role.getId());
        userRole.setGrantedAt(Instant.now());
        userRoleRepository.save(userRole);

        LoginResponse afterGrant = authApplication.currentUser(user.getId());

        assertThat(afterGrant.permissionVersion()).isNotEqualTo(beforeGrant.permissionVersion());
    }

    private RolePo role(String code) {
        RolePo role = new RolePo();
        role.setRoleCode(code);
        role.setRoleName(code);
        role.setStatus(RbacStatus.ENABLED);
        return role;
    }

    private void saveNonImDefaultRegisterRoles() {
        roleRepository.save(role("CHAT_BASIC"));
        roleRepository.save(role("RAG_USER"));
        roleRepository.save(role("AGENT_DASHBOARD_USER"));
        roleRepository.save(role("AGENT_MCP_USER"));
    }

    private PermissionPo permission(String code, PermissionType type) {
        PermissionPo permission = new PermissionPo();
        permission.setPermCode(code);
        permission.setPermName(code);
        permission.setPermType(type);
        permission.setStatus(PermissionStatus.ENABLED);
        return permissionRepository.save(permission);
    }

    private PermissionPo menuPermission(String code, String routeName, String routePath) {
        PermissionPo permission = permission(code, PermissionType.MENU);
        MenuPo menu = new MenuPo();
        menu.setPermissionId(permission.getId());
        menu.setRouteName(routeName);
        menu.setRoutePath(routePath);
        menu.setCacheable(true);
        menuRepository.save(menu);
        return permission;
    }

    private void grant(RolePo role, PermissionPo permission) {
        RolePermissionPo rolePermission = new RolePermissionPo();
        rolePermission.setRoleId(role.getId());
        rolePermission.setPermissionId(permission.getId());
        rolePermission.setGrantedAt(Instant.now());
        rolePermissionRepository.save(rolePermission);
    }

    private LoginRequest loginRequest(String account, String password) {
        EncryptedPassword encrypted = encryptPassword(password);
        return new LoginRequest(account, encrypted.encryptedPassword(), encrypted.passwordKeyId());
    }

    private RegisterRequest registerRequest(String accountNo, String username, String password, String nickname,
                                            String email, String mobile, String avatarUrl) {
        EncryptedPassword encrypted = encryptPassword(password);
        return new RegisterRequest(accountNo, username, encrypted.encryptedPassword(), encrypted.passwordKeyId(),
                nickname, email, mobile, avatarUrl);
    }

    private EncryptedPassword encryptPassword(String password) {
        try {
            var key = passwordTransportEncryptionService.currentKey();
            byte[] publicKeyBytes = Base64.getDecoder().decode(key.publicKey());
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicKeyBytes));
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, new OAEPParameterSpec(
                    "SHA-256",
                    "MGF1",
                    MGF1ParameterSpec.SHA256,
                    PSource.PSpecified.DEFAULT
            ));
            String encryptedPassword = Base64.getEncoder()
                    .encodeToString(cipher.doFinal(password.getBytes(StandardCharsets.UTF_8)));
            return new EncryptedPassword(encryptedPassword, key.keyId());
        } catch (Exception e) {
            throw new IllegalStateException("failed to encrypt test password", e);
        }
    }

    private record EncryptedPassword(String encryptedPassword, String passwordKeyId) {
    }

}
