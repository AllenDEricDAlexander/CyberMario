package top.egon.mario.agent.model.provider.dashscope;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import top.egon.mario.agent.model.api.ModelOptions;
import top.egon.mario.agent.model.api.ModelProviderType;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies DashScope option mapping without hiding upstream model choices.
 */
class DashScopeModelProviderAdapterTests {

    @Test
    void toChatOptionsMapsCommonAndProviderOptions() {
        DashScopeModelProviderAdapter adapter = new DashScopeModelProviderAdapter(
                DashScopeApi.builder().apiKey("test-api-key").build());
        ModelOptions options = new ModelOptions(
                new BigDecimal("0.7"),
                2048,
                new BigDecimal("0.9"),
                40,
                true,
                512,
                true,
                true,
                Map.of(
                        "seed", 12,
                        "repetitionPenalty", new BigDecimal("1.1"),
                        "extraBody", Map.of("custom_flag", true)
                )
        );

        DashScopeChatOptions chatOptions = adapter.toChatOptions("qwen-plus", options);

        assertThat(chatOptions.getModel()).isEqualTo("qwen-plus");
        assertThat(chatOptions.getTemperature()).isEqualTo(0.7);
        assertThat(chatOptions.getMaxTokens()).isEqualTo(2048);
        assertThat(chatOptions.getTopP()).isEqualTo(0.9);
        assertThat(chatOptions.getTopK()).isEqualTo(40);
        assertThat(chatOptions.getEnableThinking()).isTrue();
        assertThat(chatOptions.getThinkingBudget()).isEqualTo(512);
        assertThat(chatOptions.getEnableSearch()).isTrue();
        assertThat(chatOptions.getMultiModel()).isTrue();
        assertThat(chatOptions.getSeed()).isEqualTo(12);
        assertThat(chatOptions.getRepetitionPenalty()).isEqualTo(1.1);
        assertThat(chatOptions.getExtraBody()).containsEntry("custom_flag", true);
    }

    @Test
    void createReturnsDashScopeChatModelWithRequestedDefaults() {
        DashScopeModelProviderAdapter adapter = new DashScopeModelProviderAdapter(
                DashScopeApi.builder().apiKey("test-api-key").build());
        ModelOptions options = new ModelOptions(new BigDecimal("0.3"), 128, null, null,
                false, null, null, null, Map.of());

        ChatModel chatModel = adapter.create("qwen-turbo", options);

        assertThat(chatModel.getDefaultOptions()).isInstanceOf(DashScopeChatOptions.class);
        DashScopeChatOptions defaultOptions = (DashScopeChatOptions) chatModel.getDefaultOptions();
        assertThat(defaultOptions.getModel()).isEqualTo("qwen-turbo");
        assertThat(defaultOptions.getTemperature()).isEqualTo(0.3);
        assertThat(defaultOptions.getMaxTokens()).isEqualTo(128);
        assertThat(defaultOptions.getEnableThinking()).isFalse();
    }

    @Test
    void providerReturnsDashScope() {
        DashScopeModelProviderAdapter adapter = new DashScopeModelProviderAdapter(
                DashScopeApi.builder().apiKey("test-api-key").build());

        assertThat(adapter.provider()).isEqualTo(ModelProviderType.DASHSCOPE);
    }

}
