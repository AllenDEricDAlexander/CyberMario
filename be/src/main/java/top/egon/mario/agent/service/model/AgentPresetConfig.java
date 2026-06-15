package top.egon.mario.agent.service.model;

import top.egon.mario.agent.model.dto.request.ModelOptions;

/**
 * Persisted or request-level agent debug preset configuration.
 */
public record AgentPresetConfig(
        AgentModelConfig modelConfig,
        ModelOptions modelOptions,
        String systemPrompt,
        AgentToolConfig toolConfig,
        AgentOptions agentOptions
) {
}
