package top.egon.mario.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import top.egon.mario.common.api.TraceContext;
import top.egon.mario.rbac.service.RbacException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies business exception status mapping remains separate from access-token refresh signaling.
 */
class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void keepsRbacBusinessExceptionsAsBadRequestEvenWhenCodeMentionsTokenExpired() {
        var response = handler.handleRbacException(new RbacException("AUTH_TOKEN_EXPIRED", "token expired"))
                .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-1"))
                .block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("AUTH_TOKEN_EXPIRED");
    }

}
