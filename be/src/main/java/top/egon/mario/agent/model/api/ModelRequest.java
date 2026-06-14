package top.egon.mario.agent.model.api;

/**
 * Request for resolving a concrete model from upstream-selected provider, model and options.
 */
public record ModelRequest(
        ModelProviderType provider,
        String model,
        ModelOptions options,
        ModelCallContext context
) {
}
