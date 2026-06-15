package top.egon.mario.agent.dto.request;

import jakarta.validation.constraints.NotBlank;
import top.egon.mario.agent.service.model.AgentPresetConfig;

/**
 * Request body for a dynamic agent debug chat turn.
 */
public record AgentDebugChatRequest(
        @NotBlank String message,
        String threadId,
        Long presetId,
        AgentPresetConfig overrides
) {
}
