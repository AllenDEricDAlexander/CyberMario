package top.egon.mario.agent.model.api;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Provider-neutral model options supplied by upstream callers.
 */
public record ModelOptions(
        BigDecimal temperature,
        Integer maxTokens,
        BigDecimal topP,
        Integer topK,
        Boolean enableThinking,
        Integer thinkingBudget,
        Boolean enableSearch,
        Boolean multiModel,
        Map<String, Object> providerOptions
) {

    public ModelOptions {
        providerOptions = providerOptions == null ? Map.of() : Map.copyOf(providerOptions);
    }

}
