package top.egon.mario.agent.model.api;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * Resolved model and audit metadata returned by the model factory.
 */
public record ModelResolveResult(
        ChatModel chatModel,
        ModelProviderType provider,
        String model,
        ModelOptions options,
        ModelCallContext context,
        ChatOptions chatOptions
) {
}
