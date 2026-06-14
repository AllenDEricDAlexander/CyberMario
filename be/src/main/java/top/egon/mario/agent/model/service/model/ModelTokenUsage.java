package top.egon.mario.agent.model.service.model;

import top.egon.mario.agent.model.po.enums.TokenUsageSource;

/**
 * Token usage collected for one model call.
 */
public record ModelTokenUsage(
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        TokenUsageSource source
) {

    public ModelTokenUsage {
        source = source == null ? TokenUsageSource.UNAVAILABLE : source;
    }

    public static ModelTokenUsage unavailable() {
        return new ModelTokenUsage(null, null, null, TokenUsageSource.UNAVAILABLE);
    }

}
