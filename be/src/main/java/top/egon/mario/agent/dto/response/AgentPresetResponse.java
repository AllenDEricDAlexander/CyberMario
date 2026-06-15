package top.egon.mario.agent.dto.response;

import top.egon.mario.agent.service.model.AgentPresetConfig;

import java.time.Instant;

/**
 * Saved agent debug preset returned to the frontend.
 */
public record AgentPresetResponse(
        Long id,
        String name,
        String description,
        AgentPresetConfig config,
        boolean enabled,
        Long createdBy,
        Long updatedBy,
        Instant createdAt,
        Instant updatedAt
) {
}
