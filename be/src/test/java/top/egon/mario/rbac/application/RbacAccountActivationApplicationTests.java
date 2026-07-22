package top.egon.mario.rbac.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import top.egon.mario.rbac.activation.model.AccountActivationResult;
import top.egon.mario.rbac.activation.model.CompleteAccountActivationCommand;
import top.egon.mario.rbac.activation.service.ActivationFailureRateLimiter;
import top.egon.mario.rbac.activation.service.RbacAccountActivationTokenService;
import top.egon.mario.rbac.dto.request.CompleteAccountActivationRequest;
import top.egon.mario.rbac.service.RbacException;
import top.egon.mario.rbac.activation.delivery.ActivationLinkDelivery;
import top.egon.mario.rbac.service.RbacUserService;
import org.springframework.transaction.support.TransactionOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

/**
 * Verifies public activation orchestration counts only security-relevant failures.
 */
class RbacAccountActivationApplicationTests {

    private final RbacAccountActivationTokenService tokenService = mock(RbacAccountActivationTokenService.class);
    private final ActivationFailureRateLimiter rateLimiter = mock(ActivationFailureRateLimiter.class);
    private final RbacUserService userService = mock(RbacUserService.class);
    private final ActivationLinkDelivery delivery = mock(ActivationLinkDelivery.class);
    private final TransactionOperations transactionOperations = mock(TransactionOperations.class);
    private final ActivationFailureRateLimiter.AttemptReservation reservation =
            new ActivationFailureRateLimiter.AttemptReservation(true, "hashed-key", "attempt-1");
    private final RbacAccountActivationApplication application =
            new RbacAccountActivationApplication(tokenService, rateLimiter, userService,
                    delivery, transactionOperations);

    @ParameterizedTest
    @ValueSource(strings = {
            "AUTH_ACTIVATION_TOKEN_INVALID",
            "AUTH_ACTIVATION_USER_UNAVAILABLE",
            "AUTH_PASSWORD_KEY_INVALID",
            "AUTH_PASSWORD_DECRYPT_FAILED",
            "RBAC_USER_PASSWORD_INVALID"
    })
    void recordsOnlySecurityRelevantActivationFailures(String code) {
        CompleteAccountActivationRequest request = request();
        given(rateLimiter.beginAttempt("127.0.0.1")).willReturn(reservation);
        doThrow(new RbacException(code, "rejected")).when(tokenService).activate(any());

        assertThatThrownBy(() -> application.complete(request, "127.0.0.1", "test"))
                .isInstanceOf(RbacException.class);

        then(rateLimiter).should().beginAttempt("127.0.0.1");
        then(rateLimiter).should(never()).release(any());
    }

    @Test
    void unrelatedFailuresAreNotCounted() {
        CompleteAccountActivationRequest request = request();
        given(rateLimiter.beginAttempt("127.0.0.1")).willReturn(reservation);
        doThrow(new RbacException("RBAC_USER_NOT_FOUND", "rejected")).when(tokenService).activate(any());

        assertThatThrownBy(() -> application.complete(request, "127.0.0.1", "test"))
                .isInstanceOf(RbacException.class);

        then(rateLimiter).should().beginAttempt("127.0.0.1");
        then(rateLimiter).should().release(reservation);
    }

    @Test
    void successfulActivationDoesNotIncrementFailureCount() {
        CompleteAccountActivationRequest request = request();
        given(rateLimiter.beginAttempt("127.0.0.1")).willReturn(reservation);
        given(tokenService.activate(any())).willReturn(new AccountActivationResult(7L, "mario"));

        application.complete(request, "127.0.0.1", "test-agent");

        ArgumentCaptor<CompleteAccountActivationCommand> command =
                ArgumentCaptor.forClass(CompleteAccountActivationCommand.class);
        then(tokenService).should().activate(command.capture());
        assertThat(command.getValue()).isEqualTo(new CompleteAccountActivationCommand(
                "raw-token", "key-1", "encrypted-password", "127.0.0.1", "test-agent"));
        then(rateLimiter).should().release(reservation);
    }

    @Test
    void releaseFailureDoesNotTurnSuccessfulActivationIntoFailure() {
        CompleteAccountActivationRequest request = request();
        given(rateLimiter.beginAttempt("127.0.0.1")).willReturn(reservation);
        given(tokenService.activate(any())).willReturn(new AccountActivationResult(7L, "mario"));
        doThrow(new IllegalStateException("redis unavailable")).when(rateLimiter).release(reservation);

        assertThatCode(() -> application.complete(request, "127.0.0.1", "test"))
                .doesNotThrowAnyException();
    }

    @Test
    void releaseFailureDoesNotMaskNonCountedBusinessFailure() {
        CompleteAccountActivationRequest request = request();
        RbacException original = new RbacException("RBAC_USER_NOT_FOUND", "rejected");
        given(rateLimiter.beginAttempt("127.0.0.1")).willReturn(reservation);
        doThrow(original).when(tokenService).activate(any());
        doThrow(new IllegalStateException("redis unavailable")).when(rateLimiter).release(reservation);

        assertThatThrownBy(() -> application.complete(request, "127.0.0.1", "test"))
                .isSameAs(original);
    }

    private CompleteAccountActivationRequest request() {
        return new CompleteAccountActivationRequest("raw-token", "key-1", "encrypted-password");
    }
}
