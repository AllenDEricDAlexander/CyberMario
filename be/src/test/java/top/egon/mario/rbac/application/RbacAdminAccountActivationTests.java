package top.egon.mario.rbac.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockReset;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.mario.rbac.activation.delivery.ActivationDeliveryMode;
import top.egon.mario.rbac.activation.delivery.ActivationLinkDelivery;
import top.egon.mario.rbac.activation.model.ActivationTokenIssueReason;
import top.egon.mario.rbac.activation.model.IssuedActivationToken;
import top.egon.mario.rbac.activation.service.RbacAccountActivationTokenService;
import top.egon.mario.rbac.activation.store.RbacOneTimeTokenStore;
import top.egon.mario.rbac.dto.enums.ActivationStatus;
import top.egon.mario.rbac.dto.request.CreateUserRequest;
import top.egon.mario.rbac.dto.request.UpdateUserRequest;
import top.egon.mario.rbac.dto.response.ActivationDeliveryResponse;
import top.egon.mario.rbac.dto.response.AdminUserCreateResponse;
import top.egon.mario.rbac.po.RolePo;
import top.egon.mario.rbac.po.UserPo;
import top.egon.mario.rbac.po.enums.RbacStatus;
import top.egon.mario.rbac.repository.AuditLogRepository;
import top.egon.mario.rbac.repository.OneTimeTokenRepository;
import top.egon.mario.rbac.repository.RoleRepository;
import top.egon.mario.rbac.repository.UserRepository;
import top.egon.mario.rbac.repository.UserRoleRepository;
import top.egon.mario.rbac.service.RbacException;
import top.egon.mario.rbac.service.RbacUserService;

import java.net.URI;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

/**
 * Verifies administrator pending-account creation, delivery, reissue, and revocation lifecycle.
 */
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class RbacAdminAccountActivationTests {

    @Autowired
    private RbacAccountActivationApplication application;
    @Autowired
    private RbacUserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private OneTimeTokenRepository oneTimeTokenRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private RbacOneTimeTokenStore tokenStore;

    @MockitoBean
    private ChatModel chatModel;
    @MockitoBean
    private ActivationLinkDelivery delivery;
    @MockitoSpyBean(reset = MockReset.AFTER)
    private RbacAccountActivationTokenService tokenService;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        oneTimeTokenRepository.deleteAll();
        userRoleRepository.deleteAll();
        roleRepository.deleteAll();
        userRepository.deleteAll();
        willAnswer(invocation -> deliveryResponse(invocation.getArgument(0)))
                .given(delivery).deliver(any());
    }

    @Test
    void adminCreateCommitsPendingUserRolesAndTokenBeforeDelivery() {
        RolePo role = roleRepository.save(enabledRole("RBAC_VIEWER"));
        CreateUserRequest request = createRequest("mario", "mario@example.com", Set.of(role.getId()));
        willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return deliveryResponse(invocation.getArgument(0));
        }).given(delivery).deliver(any());

        AdminUserCreateResponse response = application.createPendingUser(request, 99L);

        UserPo saved = userRepository.findById(response.user().getId()).orElseThrow();
        assertThat(saved.getEmail()).isEqualTo("mario@example.com");
        assertThat(saved.getStatus()).isEqualTo(RbacStatus.ENABLED);
        assertThat(saved.isLocked()).isFalse();
        assertThat(saved.isPasswordExpired()).isTrue();
        assertThat(saved.getActivatedAt()).isNull();
        assertThat(passwordEncoder.matches("known-admin-password", saved.getPasswordHash())).isFalse();
        assertThat(oneTimeTokenRepository.findAll()).hasSize(1);
        assertThat(userRoleRepository.findByUserId(saved.getId()))
                .extracting("roleId").containsExactly(role.getId());
        assertThat(response.user().getActivationStatus()).isEqualTo(ActivationStatus.PENDING_ACTIVATION);
        assertThat(response.activationDelivery().mockActivationUrl()).contains("/activate#token=");
    }

    @Test
    void tokenIssuanceFailureRollsBackUserRolesAuditAndDelivery() {
        RolePo role = roleRepository.save(enabledRole("RBAC_VIEWER"));
        doThrow(new RbacException("AUTH_ACTIVATION_TOKEN_ISSUE_FAILED", "forced failure"))
                .when(tokenService).issueForUser(anyLong(), eq(99L), eq(ActivationTokenIssueReason.ISSUED));

        assertThatThrownBy(() -> application.createPendingUser(
                createRequest("rollback", "rollback@example.com", Set.of(role.getId())), 99L))
                .extracting("code").isEqualTo("AUTH_ACTIVATION_TOKEN_ISSUE_FAILED");

        assertThat(userRepository.findByAccountNoAndDeletedFalse("rollback")).isEmpty();
        assertThat(userRoleRepository.findAll()).isEmpty();
        assertThat(oneTimeTokenRepository.findAll()).isEmpty();
        assertThat(auditLogRepository.findAll()).isEmpty();
        then(delivery).should(never()).deliver(any());
    }

    @Test
    void duplicateEmailCreatesNeitherPendingUserNorToken() {
        activatedUser("existing", "taken@example.com", "existing-password");

        assertThatThrownBy(() -> application.createPendingUser(
                createRequest("duplicate", "taken@example.com", Set.of()), 99L))
                .extracting("code").isEqualTo("RBAC_USER_EMAIL_DUPLICATED");
        assertThat(userRepository.findAll()).hasSize(1);
        assertThat(oneTimeTokenRepository.findAll()).isEmpty();
    }

    @Test
    void reissueReplacesTheOldRawTokenAndAuditsWithoutTokenContent() {
        AdminUserCreateResponse created = application.createPendingUser(
                createRequest("luigi", "luigi@example.com", Set.of()), 99L);
        String firstRaw = rawToken(created.activationDelivery());

        ActivationDeliveryResponse reissued = application.reissue(created.user().getId(), 99L);
        String secondRaw = rawToken(reissued);

        assertThat(secondRaw).isNotEqualTo(firstRaw);
        assertThat(oneTimeTokenRepository.findAll()).singleElement()
                .extracting("tokenHash")
                .isEqualTo(tokenStore.hash(secondRaw));
        assertThat(auditLogRepository.findAll()).extracting("action")
                .contains("AUTH_ACTIVATION_TOKEN_ISSUED", "AUTH_ACTIVATION_TOKEN_REISSUED");
        assertThat(auditLogRepository.findAll()).allSatisfy(audit -> {
            assertThat(String.valueOf(audit.getBeforeJson())).doesNotContain(firstRaw, secondRaw);
            assertThat(String.valueOf(audit.getAfterJson())).doesNotContain(firstRaw, secondRaw);
        });
    }

    @Test
    void activatedUserCannotReissueAndCanStillUseAdminPasswordReset() {
        UserPo user = activatedUser("peach", "peach@example.com", "old-password-123");

        assertThatThrownBy(() -> application.reissue(user.getId(), 99L))
                .extracting("code").isEqualTo("AUTH_USER_ALREADY_ACTIVATED");
        userService.resetPassword(user.getId(), "new-password-123");
        assertThat(passwordEncoder.matches("new-password-123",
                userRepository.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void pendingUserCannotBypassActivationWithAdminPasswordReset() {
        AdminUserCreateResponse created = application.createPendingUser(
                createRequest("toad", "toad@example.com", Set.of()), 99L);
        String before = userRepository.findById(created.user().getId()).orElseThrow().getPasswordHash();

        assertThatThrownBy(() -> userService.resetPassword(created.user().getId(), "new-password-123"))
                .extracting("code").isEqualTo("AUTH_USER_NOT_ACTIVATED");
        assertThat(userRepository.findById(created.user().getId()).orElseThrow().getPasswordHash())
                .isEqualTo(before);
        assertThat(oneTimeTokenRepository.findAll()).hasSize(1);
    }

    @Test
    void pendingEmailChangeRevokesWithoutIssuingAReplacement() {
        AdminUserCreateResponse created = application.createPendingUser(
                createRequest("daisy", "daisy@example.com", Set.of()), 99L);
        UpdateUserRequest update = new UpdateUserRequest();
        update.setEmail("new-daisy@example.com");

        userService.updateUser(created.user().getId(), update);

        assertThat(oneTimeTokenRepository.findAll()).isEmpty();
        assertThat(auditLogRepository.findAll()).extracting("action")
                .contains("AUTH_ACTIVATION_TOKEN_REVOKED");
    }

    @Test
    void disablingThenReenablingPendingUserDoesNotCreateAToken() {
        AdminUserCreateResponse created = application.createPendingUser(
                createRequest("wario", "wario@example.com", Set.of()), 99L);

        userService.updateStatus(created.user().getId(), top.egon.mario.rbac.dto.enums.RbacStatus.DISABLED);
        assertThat(oneTimeTokenRepository.findAll()).isEmpty();
        userService.updateStatus(created.user().getId(), top.egon.mario.rbac.dto.enums.RbacStatus.ENABLED);
        assertThat(oneTimeTokenRepository.findAll()).isEmpty();
    }

    @Test
    void lockingPendingUserRevokesItsToken() {
        AdminUserCreateResponse created = application.createPendingUser(
                createRequest("rosalina", "rosalina@example.com", Set.of()), 99L);
        UpdateUserRequest update = new UpdateUserRequest();
        update.setEmail("rosalina@example.com");
        update.setLocked(true);

        userService.updateUser(created.user().getId(), update);

        assertThat(oneTimeTokenRepository.findAll()).isEmpty();
    }

    @Test
    void deletingPendingUserRevokesItsToken() {
        AdminUserCreateResponse created = application.createPendingUser(
                createRequest("yoshi", "yoshi@example.com", Set.of()), 99L);

        userService.deleteUser(created.user().getId(), 99L);

        assertThat(oneTimeTokenRepository.findAll()).isEmpty();
    }

    private ActivationDeliveryResponse deliveryResponse(IssuedActivationToken issued) {
        return new ActivationDeliveryResponse(ActivationDeliveryMode.MOCK,
                issued.token().getExpiresAt(), "http://localhost:5173/activate#token="
                + issued.token().getTokenValue());
    }

    private CreateUserRequest createRequest(String account, String email, Set<Long> roleIds) {
        CreateUserRequest request = new CreateUserRequest();
        request.setAccountNo(account);
        request.setUsername(account);
        request.setNickname(account);
        request.setEmail(email);
        request.setRoleIds(roleIds);
        return request;
    }

    private RolePo enabledRole(String code) {
        RolePo role = new RolePo();
        role.setRoleCode(code);
        role.setRoleName(code);
        role.setStatus(RbacStatus.ENABLED);
        return role;
    }

    private UserPo activatedUser(String account, String email, String password) {
        UserPo user = new UserPo();
        user.setAccountNo(account);
        user.setUsername(account);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus(RbacStatus.ENABLED);
        user.setActivatedAt(Instant.now());
        return userRepository.save(user);
    }

    private String rawToken(ActivationDeliveryResponse response) {
        return URI.create(response.mockActivationUrl()).getFragment().substring("token=".length());
    }
}
