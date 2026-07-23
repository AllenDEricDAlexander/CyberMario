package top.egon.mario.rbac.activation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.authentication.ott.OneTimeTokenAuthenticationToken;
import org.springframework.security.authentication.ott.reactive.ReactiveOneTimeTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockReset;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import top.egon.mario.rbac.activation.model.AccountActivationResult;
import top.egon.mario.rbac.activation.model.ActivationTokenIssueReason;
import top.egon.mario.rbac.activation.model.CompleteAccountActivationCommand;
import top.egon.mario.rbac.activation.model.IssuedActivationToken;
import top.egon.mario.rbac.activation.store.RbacOneTimeTokenStore;
import top.egon.mario.rbac.po.OneTimeTokenPo;
import top.egon.mario.rbac.po.UserPo;
import top.egon.mario.rbac.po.enums.OneTimeTokenPurpose;
import top.egon.mario.rbac.po.enums.RbacStatus;
import top.egon.mario.rbac.repository.AuditLogRepository;
import top.egon.mario.rbac.repository.OneTimeTokenRepository;
import top.egon.mario.rbac.repository.UserRepository;
import top.egon.mario.rbac.service.RbacException;
import top.egon.mario.rbac.service.security.PasswordTransportEncryptionService;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willAnswer;

/**
 * Verifies hashing, TTL, replacement, rollback, successful activation, and concurrency.
 */
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class RbacReactiveOneTimeTokenServiceTests {

    @Autowired
    private RbacAccountActivationTokenService tokenService;
    @MockitoSpyBean(reset = MockReset.AFTER)
    private RbacOneTimeTokenStore tokenStore;
    @Autowired
    private OneTimeTokenRepository oneTimeTokenRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private PasswordTransportEncryptionService passwordTransportEncryptionService;
    @Autowired
    private Clock clock;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        oneTimeTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void issueStoresOnlySha256HashAndExpiresAfterTwentyFourHours() {
        UserPo user = pendingUser("mario", "mario@example.com");
        Instant before = clock.instant();

        IssuedActivationToken issued = tokenService.issueForUser(
                user.getId(), 99L, ActivationTokenIssueReason.ISSUED);

        OneTimeTokenPo stored = oneTimeTokenRepository.findAll().getFirst();
        assertThat(Base64.getUrlDecoder().decode(issued.token().getTokenValue())).hasSize(32);
        assertThat(stored.getTokenHash()).hasSize(64)
                .isNotEqualTo(issued.token().getTokenValue());
        assertThat(stored.getUserId()).isEqualTo(user.getId());
        assertThat(stored.getPurpose()).isEqualTo(OneTimeTokenPurpose.ACCOUNT_ACTIVATION);
        assertThat(stored.getExpiresAt()).isBetween(before.plus(Duration.ofHours(24)),
                clock.instant().plus(Duration.ofHours(24)));
    }

    @Test
    void reactiveSpiReplacesAndConsumesTheSingleCurrentToken() {
        UserPo user = pendingUser("luigi", "luigi@example.com");
        ReactiveOneTimeTokenService springService = tokenService;

        OneTimeToken first = springService.generate(new GenerateOneTimeTokenRequest(user.getUsername(),
                Duration.ofHours(24))).block();
        OneTimeToken second = springService.generate(new GenerateOneTimeTokenRequest(user.getUsername(),
                Duration.ofHours(24))).block();

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(second.getTokenValue()).isNotEqualTo(first.getTokenValue());
        assertThat(springService.consume(OneTimeTokenAuthenticationToken
                .unauthenticated(first.getTokenValue())).block()).isNull();
        assertThat(springService.consume(OneTimeTokenAuthenticationToken
                .unauthenticated(second.getTokenValue())).block()).isNotNull();
        assertThat(oneTimeTokenRepository.findAll()).isEmpty();
    }

    @Test
    void activateAtomicallySetsTheFirstPasswordAndConsumesTheToken() {
        UserPo user = pendingUser("peach", "peach@example.com");
        IssuedActivationToken issued = tokenService.issueForUser(
                user.getId(), 99L, ActivationTokenIssueReason.ISSUED);
        EncryptedPassword password = encryptPassword("new-password-123");

        AccountActivationResult result = tokenService.activate(new CompleteAccountActivationCommand(
                issued.token().getTokenValue(), password.passwordKeyId(), password.encryptedPassword(),
                "127.0.0.1", "activation-test"));

        UserPo activated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(result.userId()).isEqualTo(user.getId());
        assertThat(activated.getActivatedAt()).isNotNull();
        assertThat(activated.isPasswordExpired()).isFalse();
        assertThat(passwordEncoder.matches("new-password-123", activated.getPasswordHash())).isTrue();
        assertThat(oneTimeTokenRepository.findAll()).isEmpty();
        assertThat(auditLogRepository.findAll())
                .extracting("action")
                .contains("AUTH_ACCOUNT_ACTIVATED");
    }

    @Test
    void invalidExpiredAndAlreadyConsumedTokensShareOneErrorCode() {
        assertActivationCode("not-a-token", "AUTH_ACTIVATION_TOKEN_INVALID");

        UserPo expiredUser = pendingUser("expired", "expired@example.com");
        String rawExpired = "expired-token";
        oneTimeTokenRepository.save(tokenRow(expiredUser.getId(), rawExpired, clock.instant().minusSeconds(1)));
        assertActivationCode(rawExpired, "AUTH_ACTIVATION_TOKEN_INVALID");

        UserPo consumedUser = pendingUser("consumed", "consumed@example.com");
        IssuedActivationToken consumed = tokenService.issueForUser(
                consumedUser.getId(), 99L, ActivationTokenIssueReason.ISSUED);
        EncryptedPassword password = encryptPassword("new-password-123");
        tokenService.activate(command(consumed.token().getTokenValue(), password));
        assertActivationCode(consumed.token().getTokenValue(), "AUTH_ACTIVATION_TOKEN_INVALID");
    }

    @Test
    void passwordPolicyFailureRollsBackAndLeavesTokenUsable() {
        UserPo user = pendingUser("toad", "toad@example.com");
        IssuedActivationToken issued = tokenService.issueForUser(
                user.getId(), 99L, ActivationTokenIssueReason.ISSUED);

        assertThatThrownBy(() -> tokenService.activate(command(
                issued.token().getTokenValue(), encryptPassword("short"))))
                .extracting("code")
                .isEqualTo("RBAC_USER_PASSWORD_INVALID");
        assertThat(oneTimeTokenRepository.findAll()).hasSize(1);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getActivatedAt()).isNull();

        tokenService.activate(command(issued.token().getTokenValue(),
                encryptPassword("valid-password-123")));
        assertThat(oneTimeTokenRepository.findAll()).isEmpty();
    }

    @Test
    void disabledOrLockedUserCannotCompleteActivationAndKeepsTokenForAdminRecovery() {
        UserPo disabled = pendingUser("disabled", "disabled@example.com");
        IssuedActivationToken disabledToken = tokenService.issueForUser(
                disabled.getId(), 99L, ActivationTokenIssueReason.ISSUED);
        disabled.setStatus(RbacStatus.DISABLED);
        userRepository.saveAndFlush(disabled);

        assertThatThrownBy(() -> tokenService.activate(command(disabledToken.token().getTokenValue(),
                encryptPassword("valid-password-123"))))
                .extracting("code").isEqualTo("AUTH_ACTIVATION_USER_UNAVAILABLE");

        UserPo locked = pendingUser("locked", "locked@example.com");
        IssuedActivationToken lockedToken = tokenService.issueForUser(
                locked.getId(), 99L, ActivationTokenIssueReason.ISSUED);
        locked.setLocked(true);
        userRepository.saveAndFlush(locked);

        assertThatThrownBy(() -> tokenService.activate(command(lockedToken.token().getTokenValue(),
                encryptPassword("valid-password-123"))))
                .extracting("code").isEqualTo("AUTH_ACTIVATION_USER_UNAVAILABLE");
        assertThat(oneTimeTokenRepository.findAll()).hasSize(2);
    }

    @Test
    void concurrentActivationAllowsExactlyOneSuccess() throws Exception {
        UserPo user = pendingUser("bowser", "bowser@example.com");
        IssuedActivationToken issued = tokenService.issueForUser(
                user.getId(), 99L, ActivationTokenIssueReason.ISSUED);
        EncryptedPassword password = encryptPassword("new-password-123");
        CompleteAccountActivationCommand command = command(issued.token().getTokenValue(), password);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Object> attempt = () -> {
                start.await();
                try {
                    return tokenService.activate(command);
                } catch (RbacException exception) {
                    return exception;
                }
            };
            Future<Object> first = executor.submit(attempt);
            Future<Object> second = executor.submit(attempt);
            start.countDown();
            List<Object> outcomes = List.of(first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS));

            assertThat(outcomes).filteredOn(AccountActivationResult.class::isInstance).hasSize(1);
            assertThat(outcomes).filteredOn(RbacException.class::isInstance)
                    .singleElement()
                    .extracting(value -> ((RbacException) value).getCode())
                    .isEqualTo("AUTH_ACTIVATION_TOKEN_INVALID");
            assertThat(userRepository.findById(user.getId()).orElseThrow().getActivatedAt()).isNotNull();
            assertThat(oneTimeTokenRepository.findAll()).isEmpty();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentFirstIssuanceSerializesReplacementAfterTheUserLock() throws Exception {
        UserPo user = pendingUser("waluigi", "waluigi@example.com");
        CountDownLatch bothReadMissingToken = new CountDownLatch(2);
        AtomicInteger reads = new AtomicInteger();
        willAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Optional<OneTimeTokenPo> current = (Optional<OneTimeTokenPo>) invocation.callRealMethod();
            if (reads.incrementAndGet() <= 2) {
                bothReadMissingToken.countDown();
                assertThat(bothReadMissingToken.await(5, TimeUnit.SECONDS)).isTrue();
            }
            return current;
        }).given(tokenStore).lockCurrentForUser(user.getId());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Object> attempt = () -> {
                start.await();
                try {
                    return tokenService.issueForUser(
                            user.getId(), 99L, ActivationTokenIssueReason.REISSUED);
                } catch (RuntimeException exception) {
                    return exception;
                }
            };
            Future<Object> first = executor.submit(attempt);
            Future<Object> second = executor.submit(attempt);
            start.countDown();
            List<Object> outcomes = List.of(first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS));

            assertThat(outcomes).allMatch(IssuedActivationToken.class::isInstance);
            assertThat(oneTimeTokenRepository.findAll()).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private UserPo pendingUser(String username, String email) {
        UserPo user = new UserPo();
        user.setAccountNo(username);
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("unusable-placeholder"));
        user.setStatus(RbacStatus.ENABLED);
        user.setLocked(false);
        user.setPasswordExpired(true);
        return userRepository.save(user);
    }

    private OneTimeTokenPo tokenRow(Long userId, String rawToken, Instant expiresAt) {
        OneTimeTokenPo row = new OneTimeTokenPo();
        row.setUserId(userId);
        row.setPurpose(OneTimeTokenPurpose.ACCOUNT_ACTIVATION);
        row.setTokenHash(tokenStore.hash(rawToken));
        row.setExpiresAt(expiresAt);
        row.setCreatedAt(clock.instant().minusSeconds(60));
        row.setCreatedBy(99L);
        return row;
    }

    private CompleteAccountActivationCommand command(String rawToken, EncryptedPassword password) {
        return new CompleteAccountActivationCommand(rawToken, password.passwordKeyId(),
                password.encryptedPassword(), "127.0.0.1", "activation-test");
    }

    private void assertActivationCode(String rawToken, String expectedCode) {
        assertThatThrownBy(() -> tokenService.activate(command(
                rawToken, encryptPassword("valid-password-123"))))
                .extracting("code")
                .isEqualTo(expectedCode);
    }

    private EncryptedPassword encryptPassword(String password) {
        try {
            var key = passwordTransportEncryptionService.currentKey();
            byte[] publicKeyBytes = Base64.getDecoder().decode(key.publicKey());
            PublicKey publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(publicKeyBytes));
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, new OAEPParameterSpec(
                    "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
            return new EncryptedPassword(
                    Base64.getEncoder().encodeToString(cipher.doFinal(password.getBytes(StandardCharsets.UTF_8))),
                    key.keyId());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("failed to encrypt activation test password", exception);
        }
    }

    private record EncryptedPassword(String encryptedPassword, String passwordKeyId) {
    }
}
