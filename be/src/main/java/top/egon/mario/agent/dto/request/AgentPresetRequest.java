package top.egon.mario.agent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import top.egon.mario.agent.service.model.AgentPresetConfig;

/**
 * Request body for creating or updating an agent debug preset.
 */
public record AgentPresetRequest(
        @NotBlank @Size(max = 128) String name,
        @Size(max = 512) String description,
        AgentPresetConfig config,
        Boolean enabled
) {
}
