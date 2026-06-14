package top.egon.mario.agent.model.api;

/**
 * Resolves upstream-selected chat models and attaches model-call auditing.
 */
public interface MarioModelFactory {

    /**
     * Returns a provider-backed chat model using the upstream-selected provider, model and options.
     */
    ModelResolveResult resolve(ModelRequest request);

}
