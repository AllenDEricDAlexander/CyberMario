package top.egon.mario.agent.model.service.provider;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import top.egon.mario.agent.model.dto.enums.ModelProviderType;
import top.egon.mario.agent.model.dto.request.ModelOptions;

/**
 * Creates provider-specific Spring AI chat models from normalized model requests.
 */
public interface ModelProviderAdapter {

    /**
     * Identifies the provider handled by this adapter.
     */
    ModelProviderType provider();

    /**
     * Creates a chat model for the exact model name and options supplied by the caller.
     */
    ChatModel create(String model, ModelOptions options);

    /**
     * Converts provider-neutral options into provider-specific Spring AI chat options.
     */
    ChatOptions toChatOptions(String model, ModelOptions options);

}
