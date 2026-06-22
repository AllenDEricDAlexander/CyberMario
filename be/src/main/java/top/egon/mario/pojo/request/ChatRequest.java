package top.egon.mario.pojo.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for an agent conversation turn.
 */
public record ChatRequest(
        @NotBlank String message,
        String threadId,
        String sessionId,
        @JsonAlias("memoryEnabled") Boolean memoryContextEnabled
) {

    public Boolean memoryEnabled() {
        return memoryContextEnabled;
    }
}
