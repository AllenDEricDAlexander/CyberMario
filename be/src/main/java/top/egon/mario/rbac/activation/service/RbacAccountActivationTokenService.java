package top.egon.mario.rbac.activation.service;

import org.springframework.security.authentication.ott.reactive.ReactiveOneTimeTokenService;
import top.egon.mario.rbac.activation.model.AccountActivationResult;
import top.egon.mario.rbac.activation.model.ActivationTokenIssueReason;
import top.egon.mario.rbac.activation.model.CompleteAccountActivationCommand;
import top.egon.mario.rbac.activation.model.IssuedActivationToken;

/**
 * Extends Spring Security's reactive OTT contract with blocking-scheduler activation operations.
 */
public interface RbacAccountActivationTokenService extends ReactiveOneTimeTokenService {

    IssuedActivationToken issueForUser(Long userId, Long actorUserId, ActivationTokenIssueReason reason);

    AccountActivationResult activate(CompleteAccountActivationCommand command);

    boolean revokeForUser(Long userId, Long actorUserId, String reason);
}
