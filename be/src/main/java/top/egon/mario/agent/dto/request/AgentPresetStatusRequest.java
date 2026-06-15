package top.egon.mario.agent.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for enabling or disabling an agent debug preset.
 */
public record AgentPresetStatusRequest(@NotNull Boolean enabled) {
}
