package top.egon.mario.agent.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import top.egon.mario.agent.service.model.AgentPresetConfig;

/**
 * Request body for a dynamic agent debug chat turn.
 */
public record AgentDebugChatRequest(
        @NotBlank String message,
        String threadId,
        String sessionId,
        @JsonAlias("memoryEnabled") Boolean memoryContextEnabled,
        Boolean longTermExtractionEnabled,
        Long presetId,
        AgentPresetConfig overrides
) {

    public Boolean memoryEnabled() {
        return memoryContextEnabled;
    }
}
