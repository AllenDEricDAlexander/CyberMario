package top.egon.mario.agent.model.audit;

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
