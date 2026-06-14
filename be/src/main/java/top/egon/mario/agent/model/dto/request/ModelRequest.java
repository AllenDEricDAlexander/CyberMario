package top.egon.mario.agent.model.dto.request;

import top.egon.mario.agent.model.dto.enums.ModelProviderType;
import top.egon.mario.agent.model.service.model.ModelCallContext;

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
