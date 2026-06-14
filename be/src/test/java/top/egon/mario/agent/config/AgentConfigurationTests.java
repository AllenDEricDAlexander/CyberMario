package top.egon.mario.agent.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import top.egon.mario.agent.model.api.MarioModelFactory;
import top.egon.mario.agent.model.api.ModelProviderType;
import top.egon.mario.agent.model.api.ModelRequest;
import top.egon.mario.agent.model.api.ModelResolveResult;
import top.egon.mario.agent.model.api.ModelScenario;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies agent configuration keeps model construction behind the model factory.
 */
class AgentConfigurationTests {

    @Test
    void cyberMarioAgentResolvesChatModelThroughModelFactory() {
        AgentConfiguration configuration = new AgentConfiguration();
        StubMarioModelFactory modelFactory = new StubMarioModelFactory();

        ReactAgent agent = configuration.cyberMarioAgent(modelFactory, List.of());

        assertThat(agent).isNotNull();
        assertThat(modelFactory.request).isNotNull();
        assertThat(modelFactory.request.provider()).isEqualTo(ModelProviderType.DASHSCOPE);
        assertThat(modelFactory.request.model()).isEqualTo("qwen3.6-max-preview");
        assertThat(modelFactory.request.options().temperature()).isEqualByComparingTo(BigDecimal.valueOf(0.7));
        assertThat(modelFactory.request.options().topP()).isEqualByComparingTo(BigDecimal.valueOf(0.9));
        assertThat(modelFactory.request.options().multiModel()).isTrue();
        assertThat(modelFactory.request.options().enableThinking()).isTrue();
        assertThat(modelFactory.request.context().scenario()).isEqualTo(ModelScenario.AGENT_CHAT);
    }

    private static final class StubMarioModelFactory implements MarioModelFactory {

        private ModelRequest request;

        @Override
        public ModelResolveResult resolve(ModelRequest request) {
            this.request = request;
            return new ModelResolveResult(new StubChatModel(), request.provider(), request.model(),
                    request.options(), request.context(), ChatOptions.builder().model(request.model()).build());
        }
    }

    private static final class StubChatModel implements ChatModel {

        @Override
        public ChatResponse call(Prompt prompt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.empty();
        }
    }

}
