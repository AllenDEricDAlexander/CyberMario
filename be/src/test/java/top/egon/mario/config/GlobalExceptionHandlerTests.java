package top.egon.mario.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import top.egon.mario.agent.service.AgentException;
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

    @Test
    void mapsAgentBusinessExceptionsAsBadRequest() {
        var response = handler.handleAgentException(new AgentException("AGENT_PRESET_FORBIDDEN", "preset can only be modified by creator"))
                .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-agent"))
                .block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("AGENT_PRESET_FORBIDDEN");
        assertThat(response.getBody().message()).isEqualTo("preset can only be modified by creator");
        assertThat(response.getBody().traceId()).isEqualTo("trace-agent");
    }

}
