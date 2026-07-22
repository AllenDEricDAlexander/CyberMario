package top.egon.mario.pojo.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for an agent conversation turn.
 */
public record ChatRequest(
        @NotBlank String message,
        String threadId,
        String sessionId,
        @JsonAlias("memoryEnabled") Boolean memoryContextEnabled,
        @Size(max = 96) String memorySpaceId
) {

    public ChatRequest(String message, String threadId, String sessionId, Boolean memoryContextEnabled) {
        this(message, threadId, sessionId, memoryContextEnabled, null);
    }

    public Boolean memoryEnabled() {
        return memoryContextEnabled;
    }
}
