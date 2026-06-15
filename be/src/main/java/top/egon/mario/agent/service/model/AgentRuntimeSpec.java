package top.egon.mario.agent.service.model;

import top.egon.mario.agent.model.dto.request.ModelOptions;

/**
 * Fully resolved agent runtime configuration used to build a ReactAgent.
 */
public record AgentRuntimeSpec(
        Long presetId,
        AgentModelConfig modelConfig,
        ModelOptions modelOptions,
        String systemPrompt,
        AgentToolConfig toolConfig,
        AgentOptions agentOptions,
        String fingerprint
) {
}
