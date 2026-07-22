package top.egon.mario.rbac.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import top.egon.mario.rbac.activation.delivery.ActivationLinkDelivery;
import top.egon.mario.rbac.activation.model.ActivationTokenIssueReason;
import top.egon.mario.rbac.activation.model.CompleteAccountActivationCommand;
import top.egon.mario.rbac.activation.model.IssuedActivationToken;
import top.egon.mario.rbac.activation.service.ActivationFailureRateLimiter;
import top.egon.mario.rbac.activation.service.RbacAccountActivationTokenService;
import top.egon.mario.rbac.dto.request.CompleteAccountActivationRequest;
import top.egon.mario.rbac.dto.request.CreateUserRequest;
import top.egon.mario.rbac.dto.response.AdminUserCreateResponse;
import top.egon.mario.rbac.dto.response.ActivationDeliveryResponse;
import top.egon.mario.rbac.dto.response.UserResponse;
import top.egon.mario.rbac.service.RbacException;
import top.egon.mario.rbac.service.RbacUserService;

import java.util.Set;

/**
 * Orchestrates public account activation and security-failure rate limiting.
 */
@Service
@RequiredArgsConstructor
public class RbacAccountActivationApplication {

    private static final Set<String> COUNTED_FAILURE_CODES = Set.of(
            "AUTH_ACTIVATION_TOKEN_INVALID",
            "AUTH_ACTIVATION_USER_UNAVAILABLE",
            "AUTH_PASSWORD_KEY_INVALID",
            "AUTH_PASSWORD_DECRYPT_FAILED",
            "RBAC_USER_PASSWORD_INVALID"
    );

    private final RbacAccountActivationTokenService tokenService;
    private final ActivationFailureRateLimiter failureRateLimiter;
    private final RbacUserService userService;
    private final ActivationLinkDelivery delivery;
    private final TransactionOperations transactionOperations;

    public void complete(CompleteAccountActivationRequest request, String ip, String userAgent) {
        failureRateLimiter.assertAllowed(ip);
        try {
            tokenService.activate(new CompleteAccountActivationCommand(
                    request.token(), request.passwordKeyId(), request.encryptedPassword(), ip, userAgent));
        } catch (RbacException exception) {
            if (COUNTED_FAILURE_CODES.contains(exception.getCode())) {
                failureRateLimiter.recordFailure(ip);
            }
            throw exception;
        }
    }

    public AdminUserCreateResponse createPendingUser(CreateUserRequest request, Long actorUserId) {
        PendingActivation pending = requireTransactionResult(transactionOperations.execute(status -> {
            UserResponse user = userService.createPendingUser(request, actorUserId);
            IssuedActivationToken issued = tokenService.issueForUser(
                    user.getId(), actorUserId, ActivationTokenIssueReason.ISSUED);
            return new PendingActivation(user, issued);
        }));
        return new AdminUserCreateResponse(pending.user(), delivery.deliver(pending.issuedToken()));
    }

    public ActivationDeliveryResponse reissue(Long userId, Long actorUserId) {
        IssuedActivationToken issued = requireTransactionResult(transactionOperations.execute(status ->
                tokenService.issueForUser(userId, actorUserId, ActivationTokenIssueReason.REISSUED)));
        return delivery.deliver(issued);
    }

    private <T> T requireTransactionResult(T value) {
        if (value == null) {
            throw new IllegalStateException("account activation transaction returned no result");
        }
        return value;
    }

    private record PendingActivation(UserResponse user, IssuedActivationToken issuedToken) {
    }
}
