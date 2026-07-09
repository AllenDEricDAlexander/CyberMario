package top.egon.mario.clocktower.agent.strategy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import top.egon.mario.agent.model.dto.enums.ModelProviderType;
import top.egon.mario.agent.model.dto.enums.ModelScenario;

@ConfigurationProperties(prefix = "clocktower.agent")
public record ClocktowerAgentPolicyProperties(
        String policy,
        Llm llm
) {

    public ClocktowerAgentPolicyProperties {
        policy = policy == null || policy.isBlank() ? "HEURISTIC" : policy;
        llm = llm == null ? new Llm(false, ModelProviderType.DASHSCOPE, "qwen-plus",
                8000, 800, 500, false, ModelScenario.AGENT_CHAT) : llm;
    }

    public record Llm(
            boolean enabled,
            ModelProviderType provider,
            String model,
            int timeoutMs,
            int maxOutputChars,
            int maxSpeechChars,
            boolean debugSavePrompt,
            ModelScenario scenario
    ) {

        public Llm {
            provider = provider == null ? ModelProviderType.DASHSCOPE : provider;
            model = model == null || model.isBlank() ? "qwen-plus" : model;
            timeoutMs = timeoutMs <= 0 ? 8000 : timeoutMs;
            maxOutputChars = maxOutputChars <= 0 ? 800 : maxOutputChars;
            maxSpeechChars = maxSpeechChars <= 0 ? 500 : maxSpeechChars;
            scenario = scenario == null ? ModelScenario.AGENT_CHAT : scenario;
        }
    }
}
