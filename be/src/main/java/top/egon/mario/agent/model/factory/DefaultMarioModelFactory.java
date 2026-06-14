package top.egon.mario.agent.model.factory;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.util.StringUtils;
import top.egon.mario.agent.model.api.MarioModelFactory;
import top.egon.mario.agent.model.api.ModelFactoryException;
import top.egon.mario.agent.model.api.ModelOptions;
import top.egon.mario.agent.model.api.ModelProviderType;
import top.egon.mario.agent.model.api.ModelRequest;
import top.egon.mario.agent.model.api.ModelResolveResult;
import top.egon.mario.agent.model.audit.ModelAuditService;
import top.egon.mario.agent.model.provider.ModelProviderAdapter;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Default registry-backed model factory. Model selection stays with upstream callers.
 */
public class DefaultMarioModelFactory implements MarioModelFactory {

    private final Map<ModelProviderType, ModelProviderAdapter> adapters;
    private final ModelAuditService auditService;

    public DefaultMarioModelFactory(List<ModelProviderAdapter> adapters, ModelAuditService auditService) {
        this.adapters = new EnumMap<>(ModelProviderType.class);
        for (ModelProviderAdapter adapter : adapters) {
            this.adapters.put(adapter.provider(), adapter);
        }
        this.auditService = auditService;
    }

    @Override
    public ModelResolveResult resolve(ModelRequest request) {
        if (request == null || request.provider() == null) {
            throw new ModelFactoryException("model provider is required");
        }
        if (!StringUtils.hasText(request.model())) {
            throw new ModelFactoryException("model is required");
        }
        ModelProviderAdapter adapter = adapters.get(request.provider());
        if (adapter == null) {
            throw new ModelFactoryException("model provider is not registered: " + request.provider());
        }
        String model = request.model().trim();
        ModelOptions options = request.options();
        ChatModel chatModel = adapter.create(model, options);
        ChatOptions chatOptions = adapter.toChatOptions(model, options);
        ChatModel auditedModel = new AuditedChatModel(chatModel, auditService, request.provider(), model, options, request.context());
        return new ModelResolveResult(auditedModel, request.provider(), model, options, request.context(), chatOptions);
    }

}
