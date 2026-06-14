package top.egon.mario.agent.model.service;

import top.egon.mario.agent.model.dto.request.ModelRequest;
import top.egon.mario.agent.model.dto.response.ModelResolveResult;

/**
 * Resolves upstream-selected chat models and attaches model-call auditing.
 */
public interface MarioModelFactory {

    /**
     * Returns a provider-backed chat model using the upstream-selected provider, model and options.
     */
    ModelResolveResult resolve(ModelRequest request);

}
