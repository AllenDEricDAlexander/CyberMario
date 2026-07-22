package top.egon.mario.rbac.activation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.ott.DefaultOneTimeToken;
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.authentication.ott.OneTimeTokenAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import top.egon.mario.rbac.activation.config.RbacOttProperties;
import top.egon.mario.rbac.activation.model.AccountActivationResult;
import top.egon.mario.rbac.activation.model.ActivationTokenIssueReason;
import top.egon.mario.rbac.activation.model.CompleteAccountActivationCommand;
import top.egon.mario.rbac.activation.model.IssuedActivationToken;
import top.egon.mario.rbac.activation.model.StoredActivationToken;
import top.egon.mario.rbac.activation.store.RbacOneTimeTokenStore;
import top.egon.mario.rbac.po.OneTimeTokenPo;
import top.egon.mario.rbac.po.UserPo;
import top.egon.mario.rbac.po.enums.RbacStatus;
import top.egon.mario.rbac.repository.UserRepository;
import top.egon.mario.rbac.service.RbacAuditService;
import top.egon.mario.rbac.service.RbacException;
import top.egon.mario.rbac.service.security.PasswordTransportEncryptionService;

import java.time.Clock;
import java.time.Duration;

/**
 * Adapts persistent activation tokens to Spring Security's reactive SPI and atomic activation flow.
 */
@Service
@RequiredArgsConstructor
public class RbacReactiveOneTimeTokenService implements RbacAccountActivationTokenService {

    private final UserRepository userRepository;
    private final RbacOneTimeTokenStore tokenStore;
    private final PasswordTransportEncryptionService passwordTransportEncryptionService;
    private final PasswordEncoder passwordEncoder;
    private final RbacAuditService auditService;
    private final RbacOttProperties properties;
    private final TransactionOperations transactionOperations;
    private final Scheduler blockingScheduler;
    private final Clock clock;

    @Override
    public Mono<OneTimeToken> generate(GenerateOneTimeTokenRequest request) {
        return Mono.fromCallable(() -> {
            UserPo user = userRepository.findByUsernameAndDeletedFalse(request.getUsername())
                    .orElseThrow(this::invalidToken);
            Duration ttl = request.getExpiresIn() == null
                    ? properties.activationTokenTtl() : request.getExpiresIn();
            return requireTransactionResult(transactionOperations.execute(status ->
                    issueLocked(user.getId(), null, ActivationTokenIssueReason.ISSUED, ttl).token()));
        }).subscribeOn(blockingScheduler);
    }

    @Override
    public Mono<OneTimeToken> consume(OneTimeTokenAuthenticationToken authenticationToken) {
        return Mono.fromCallable(() -> transactionOperations.execute(status ->
                        consumeLocked(authenticationToken.getTokenValue())))
                .flatMap(Mono::justOrEmpty)
                .subscribeOn(blockingScheduler);
    }

    @Override
    public IssuedActivationToken issueForUser(Long userId, Long actorUserId,
                                              ActivationTokenIssueReason reason) {
        return requireTransactionResult(transactionOperations.execute(status ->
                issueLocked(userId, actorUserId, reason, properties.activationTokenTtl())));
    }

    @Override
    public AccountActivationResult activate(CompleteAccountActivationCommand command) {
        return requireTransactionResult(transactionOperations.execute(status -> activateLocked(command)));
    }

    @Override
    public boolean revokeForUser(Long userId, Long actorUserId, String reason) {
        return Boolean.TRUE.equals(transactionOperations.execute(status -> {
            boolean revoked = tokenStore.revoke(userId);
            if (revoked) {
                auditService.log(actorUserId, "AUTH_ACTIVATION_TOKEN_REVOKED", "USER", userId,
                        null, reason, null, null);
            }
            return revoked;
        }));
    }

    private IssuedActivationToken issueLocked(Long userId, Long actorUserId,
                                              ActivationTokenIssueReason reason, Duration ttl) {
        // A missing token row cannot be locked, so lock the user and then re-read before replacement.
        tokenStore.lockCurrentForUser(userId);
        UserPo user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new RbacException("RBAC_USER_NOT_FOUND", "user not found"));
        if (user.getActivatedAt() != null) {
            throw new RbacException("AUTH_USER_ALREADY_ACTIVATED", "user account is already activated");
        }
        ensureActivationUserAvailable(user);
        OneTimeTokenPo current = tokenStore.lockCurrentForUser(userId).orElse(null);
        IssuedActivationToken issued = tokenStore.replace(current, user.getId(), user.getUsername(),
                actorUserId, ttl);
        auditService.log(actorUserId, reason.auditAction(), "USER", user.getId(), null,
                "{\"expiresAt\":\"" + issued.token().getExpiresAt() + "\"}", null, null);
        return issued;
    }

    private OneTimeToken consumeLocked(String rawToken) {
        StoredActivationToken stored = tokenStore.findForUpdate(rawToken).orElse(null);
        if (stored == null || !stored.row().getExpiresAt().isAfter(clock.instant())) {
            return null;
        }
        UserPo user = userRepository.findByIdForUpdate(stored.row().getUserId()).orElse(null);
        if (user == null) {
            return null;
        }
        tokenStore.delete(stored.row());
        return new DefaultOneTimeToken(rawToken, user.getUsername(), stored.row().getExpiresAt());
    }

    private AccountActivationResult activateLocked(CompleteAccountActivationCommand command) {
        StoredActivationToken stored = tokenStore.findForUpdate(command.token())
                .orElseThrow(this::invalidToken);
        if (!stored.row().getExpiresAt().isAfter(clock.instant())) {
            throw invalidToken();
        }
        UserPo user = userRepository.findByIdForUpdate(stored.row().getUserId())
                .orElseThrow(this::invalidToken);
        if (user.getActivatedAt() != null) {
            throw invalidToken();
        }
        ensureActivationUserAvailable(user);
        String password = passwordTransportEncryptionService.decryptPassword(
                command.passwordKeyId(), command.encryptedPassword());
        validatePassword(password);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setPasswordExpired(false);
        user.setActivatedAt(clock.instant());
        userRepository.save(user);
        tokenStore.delete(stored.row());
        auditService.log(user.getId(), "AUTH_ACCOUNT_ACTIVATED", "USER", user.getId(),
                null, user.getUsername(), command.ip(), command.userAgent());
        return new AccountActivationResult(user.getId(), user.getUsername());
    }

    private void ensureActivationUserAvailable(UserPo user) {
        if (user.getStatus() != RbacStatus.ENABLED || user.isLocked() || user.isDeleted()) {
            throw new RbacException("AUTH_ACTIVATION_USER_UNAVAILABLE", "user cannot be activated");
        }
    }

    private void validatePassword(String password) {
        if (password.length() < 8 || password.length() > 128) {
            throw new RbacException("RBAC_USER_PASSWORD_INVALID",
                    "password length must be between 8 and 128");
        }
    }

    private RbacException invalidToken() {
        return new RbacException("AUTH_ACTIVATION_TOKEN_INVALID",
                "activation token is invalid or expired");
    }

    private <T> T requireTransactionResult(T value) {
        if (value == null) {
            throw new IllegalStateException("activation transaction returned no result");
        }
        return value;
    }
}
