package top.egon.mario.agent.model.dto.response;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import top.egon.mario.agent.model.dto.enums.ModelProviderType;
import top.egon.mario.agent.model.dto.request.ModelOptions;
import top.egon.mario.agent.model.service.model.ModelCallContext;

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
