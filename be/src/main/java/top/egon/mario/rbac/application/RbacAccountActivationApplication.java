package top.egon.mario.rbac.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.egon.mario.rbac.activation.model.CompleteAccountActivationCommand;
import top.egon.mario.rbac.activation.service.ActivationFailureRateLimiter;
import top.egon.mario.rbac.activation.service.RbacAccountActivationTokenService;
import top.egon.mario.rbac.dto.request.CompleteAccountActivationRequest;
import top.egon.mario.rbac.service.RbacException;

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
}
