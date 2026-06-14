package top.egon.mario.agent.model.factory;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import top.egon.mario.agent.model.api.MarioModelFactory;
import top.egon.mario.agent.model.api.ModelCallContext;
import top.egon.mario.agent.model.api.ModelFactoryException;
import top.egon.mario.agent.model.api.ModelOptions;
import top.egon.mario.agent.model.api.ModelProviderType;
import top.egon.mario.agent.model.api.ModelRequest;
import top.egon.mario.agent.model.api.ModelResolveResult;
import top.egon.mario.agent.model.api.ModelScenario;
import top.egon.mario.agent.model.audit.ModelAuditService;
import top.egon.mario.agent.model.provider.ModelProviderAdapter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies model factory provider dispatch without allowing model selection logic into the factory.
 */
class DefaultMarioModelFactoryTests {

    @Test
    void resolveReturnsAuditedModelFromMatchingProvider() {
        StubModelProviderAdapter adapter = new StubModelProviderAdapter(ModelProviderType.DASHSCOPE);
        ModelAuditService auditService = event -> {
        };
        MarioModelFactory factory = new DefaultMarioModelFactory(List.of(adapter), auditService);
        ModelOptions options = new ModelOptions(null, 1024, null, null, true, null, null, null, Map.of());
        ModelCallContext context = new ModelCallContext(1L, "trace-1", "session-1", "thread-1",
                ModelScenario.AGENT_CHAT, "request-1", "127.0.0.1", "JUnit");

        ModelResolveResult result = factory.resolve(new ModelRequest(
                ModelProviderType.DASHSCOPE, "qwen-plus", options, context));

        assertThat(result.provider()).isEqualTo(ModelProviderType.DASHSCOPE);
        assertThat(result.model()).isEqualTo("qwen-plus");
        assertThat(result.options()).isSameAs(options);
        assertThat(result.context()).isSameAs(context);
        assertThat(result.chatModel()).isInstanceOf(AuditedChatModel.class);
        assertThat(adapter.requestedModel).isEqualTo("qwen-plus");
        assertThat(adapter.requestedOptions).isSameAs(options);
    }

    @Test
    void resolveRequiresProvider() {
        MarioModelFactory factory = new DefaultMarioModelFactory(List.of(), event -> {
        });
        ModelCallContext context = new ModelCallContext(1L, "trace-1", "session-1", "thread-1",
                ModelScenario.AGENT_CHAT, "request-1", "127.0.0.1", "JUnit");

        assertThatThrownBy(() -> factory.resolve(new ModelRequest(null, "qwen-plus", null, context)))
                .isInstanceOf(ModelFactoryException.class)
                .hasMessageContaining("provider");
    }

    @Test
    void resolveRequiresModel() {
        MarioModelFactory factory = new DefaultMarioModelFactory(List.of(), event -> {
        });
        ModelCallContext context = new ModelCallContext(1L, "trace-1", "session-1", "thread-1",
                ModelScenario.AGENT_CHAT, "request-1", "127.0.0.1", "JUnit");

        assertThatThrownBy(() -> factory.resolve(new ModelRequest(ModelProviderType.DASHSCOPE, " ", null, context)))
                .isInstanceOf(ModelFactoryException.class)
                .hasMessageContaining("model");
    }

    @Test
    void resolveRejectsUnknownProvider() {
        MarioModelFactory factory = new DefaultMarioModelFactory(List.of(), event -> {
        });
        ModelCallContext context = new ModelCallContext(1L, "trace-1", "session-1", "thread-1",
                ModelScenario.AGENT_CHAT, "request-1", "127.0.0.1", "JUnit");

        assertThatThrownBy(() -> factory.resolve(new ModelRequest(
                ModelProviderType.DASHSCOPE, "qwen-plus", null, context)))
                .isInstanceOf(ModelFactoryException.class)
                .hasMessageContaining("DASHSCOPE");
    }

    private static final class StubModelProviderAdapter implements ModelProviderAdapter {

        private final ModelProviderType provider;
        private String requestedModel;
        private ModelOptions requestedOptions;

        private StubModelProviderAdapter(ModelProviderType provider) {
            this.provider = provider;
        }

        @Override
        public ModelProviderType provider() {
            return provider;
        }

        @Override
        public ChatModel create(String model, ModelOptions options) {
            this.requestedModel = model;
            this.requestedOptions = options;
            return new StubChatModel();
        }

        @Override
        public ChatOptions toChatOptions(String model, ModelOptions options) {
            return ChatOptions.builder().model(model).build();
        }
    }

    private static final class StubChatModel implements ChatModel {

        @Override
        public ChatResponse call(Prompt prompt) {
            throw new UnsupportedOperationException(UUID.randomUUID().toString());
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.empty();
        }
    }

}
