package top.egon.mario.agent.model.service.provider.dashscope;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import top.egon.mario.agent.model.dto.enums.ModelProviderType;
import top.egon.mario.agent.model.dto.request.ModelOptions;
import top.egon.mario.agent.model.service.provider.ModelProviderAdapter;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Creates DashScope chat models from upstream-selected model names and options.
 */
@Component
public class DashScopeModelProviderAdapter implements ModelProviderAdapter {

    private final DashScopeApi dashScopeApi;

    public DashScopeModelProviderAdapter(DashScopeApi dashScopeApi) {
        this.dashScopeApi = dashScopeApi;
    }

    @Override
    public ModelProviderType provider() {
        return ModelProviderType.DASHSCOPE;
    }

    @Override
    public ChatModel create(String model, ModelOptions options) {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(toChatOptions(model, options))
                .build();
    }

    @Override
    public DashScopeChatOptions toChatOptions(String model, ModelOptions options) {
        DashScopeChatOptions.DashScopeChatOptionsBuilder builder = DashScopeChatOptions.builder()
                .model(model);
        if (options == null) {
            return builder.build();
        }
        if (options.temperature() != null) {
            builder.temperature(toDouble(options.temperature()));
        }
        if (options.maxTokens() != null) {
            builder.maxToken(options.maxTokens());
        }
        if (options.topP() != null) {
            builder.topP(toDouble(options.topP()));
        }
        if (options.topK() != null) {
            builder.topK(options.topK());
        }
        if (options.enableThinking() != null) {
            builder.enableThinking(options.enableThinking());
        }
        if (options.thinkingBudget() != null) {
            builder.thinkingBudget(options.thinkingBudget());
        }
        if (options.enableSearch() != null) {
            builder.enableSearch(options.enableSearch());
        }
        if (options.multiModel() != null) {
            builder.multiModel(options.multiModel());
        }
        applyProviderOptions(builder, options.providerOptions());
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private void applyProviderOptions(DashScopeChatOptions.DashScopeChatOptionsBuilder builder, Map<String, Object> providerOptions) {
        if (providerOptions == null || providerOptions.isEmpty()) {
            return;
        }
        Object seed = providerOptions.get("seed");
        if (seed instanceof Number value) {
            builder.seed(value.intValue());
        }
        Object repetitionPenalty = providerOptions.get("repetitionPenalty");
        if (repetitionPenalty instanceof Number value) {
            builder.repetitionPenalty(value.doubleValue());
        }
        Object extraBody = providerOptions.get("extraBody");
        if (extraBody instanceof Map<?, ?> value) {
            builder.extraBody((Map<String, Object>) value);
        }
    }

    private double toDouble(BigDecimal value) {
        return value.doubleValue();
    }

}
