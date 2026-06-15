package top.egon.mario.agent.service.model;

import top.egon.mario.agent.model.dto.enums.ModelProviderType;

/**
 * Model selection reserved for future agent debug expansion.
 */
public record AgentModelConfig(
        ModelProviderType provider,
        String model
) {
}
