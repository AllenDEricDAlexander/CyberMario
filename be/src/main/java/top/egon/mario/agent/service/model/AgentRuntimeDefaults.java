package top.egon.mario.agent.service.model;

import top.egon.mario.agent.model.dto.enums.ModelProviderType;
import top.egon.mario.agent.model.dto.request.ModelOptions;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * Default CyberMario agent runtime configuration.
 */
public record AgentRuntimeDefaults(
        AgentModelConfig modelConfig,
        ModelOptions modelOptions,
        String systemPrompt,
        AgentToolConfig toolConfig,
        AgentOptions agentOptions
) {

    public static AgentRuntimeDefaults defaultDefaults() {
        return new AgentRuntimeDefaults(
                new AgentModelConfig(ModelProviderType.DASHSCOPE, "qwen3.6-max-preview"),
                new ModelOptions(BigDecimal.valueOf(0.7), null, BigDecimal.valueOf(0.9), null,
                        true, null, null, true, Map.of()),
                """
                        You are CyberMario, a concise and helpful conversational assistant.
                        Answer user questions directly and keep the conversation context by thread.
                        """,
                new AgentToolConfig(Set.of()),
                new AgentOptions(false, 5, 300)
        );
    }

}
