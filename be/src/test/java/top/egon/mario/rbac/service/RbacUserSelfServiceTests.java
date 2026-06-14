package top.egon.mario.rbac.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import top.egon.mario.rbac.dto.request.ChangeCurrentUserPasswordRequest;
import top.egon.mario.rbac.dto.request.UpdateCurrentUserProfileRequest;
import top.egon.mario.rbac.dto.response.UserResponse;
import top.egon.mario.rbac.po.UserPo;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies current-user profile and password self-service operations.
 */
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class RbacUserSelfServiceTests {

    @Autowired
    private RbacUserService userService;
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
    void updateCurrentUserProfileOnlyChangesSelfEditableFields() {
        UserPo user = saveUser("mario", "old-password");
        user.setRemark("operator");
        user.setLocked(true);
        user.setPasswordExpired(true);
        user = userRepository.save(user);

        UserResponse response = userService.updateCurrentUserProfile(user.getId(), new UpdateCurrentUserProfileRequest(
                "Mario",
                "mario@example.com",
                "13900000000",
                "https://example.com/mario.png"
        ));

        UserPo updated = userRepository.findByIdAndDeletedFalse(user.getId()).orElseThrow();
        assertThat(response.getNickname()).isEqualTo("Mario");
        assertThat(updated.getNickname()).isEqualTo("Mario");
        assertThat(updated.getEmail()).isEqualTo("mario@example.com");
        assertThat(updated.getMobile()).isEqualTo("13900000000");
        assertThat(updated.getAvatarUrl()).isEqualTo("https://example.com/mario.png");
        assertThat(updated.getUsername()).isEqualTo("mario");
        assertThat(updated.getRemark()).isEqualTo("operator");
        assertThat(updated.isLocked()).isTrue();
        assertThat(updated.isPasswordExpired()).isTrue();
    }

    @Test
    void updateCurrentUserProfileRejectsDuplicatedEmail() {
        UserPo user = saveUser("mario", "old-password");
        UserPo other = saveUser("luigi", "old-password");
        other.setEmail("luigi@example.com");
        userRepository.save(other);

        assertThatThrownBy(() -> userService.updateCurrentUserProfile(user.getId(), new UpdateCurrentUserProfileRequest(
                "Mario",
                "luigi@example.com",
                null,
                null
        )))
                .isInstanceOf(RbacException.class)
                .hasMessageContaining("email already exists");
    }

    @Test
    void changeCurrentUserPasswordRequiresCurrentPasswordAndUpdatesHash() {
        UserPo user = saveUser("mario", "old-password");
        user.setPasswordExpired(true);
        user = userRepository.save(user);

        userService.changeCurrentUserPassword(user.getId(), new ChangeCurrentUserPasswordRequest(
                "old-password",
                "new-password123",
                "new-password123"
        ));

        UserPo updated = userRepository.findByIdAndDeletedFalse(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("new-password123", updated.getPasswordHash())).isTrue();
        assertThat(updated.isPasswordExpired()).isFalse();
    }

    @Test
    void changeCurrentUserPasswordRejectsWrongCurrentPassword() {
        UserPo user = saveUser("mario", "old-password");

        assertThatThrownBy(() -> userService.changeCurrentUserPassword(user.getId(), new ChangeCurrentUserPasswordRequest(
                "wrong-password",
                "new-password123",
                "new-password123"
        )))
                .isInstanceOf(RbacException.class)
                .hasMessageContaining("current password is invalid");
    }

    private UserPo saveUser(String username, String password) {
        UserPo user = new UserPo();
        user.setUsername(username);
        user.setNickname(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus(RbacStatus.ENABLED);
        return userRepository.save(user);
    }

}
