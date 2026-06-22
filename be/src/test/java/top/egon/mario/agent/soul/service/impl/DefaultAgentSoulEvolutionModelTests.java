package top.egon.mario.agent.soul.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import top.egon.mario.agent.model.dto.enums.ModelProviderType;
import top.egon.mario.agent.model.dto.enums.ModelScenario;
import top.egon.mario.agent.model.dto.request.ModelRequest;
import top.egon.mario.agent.model.dto.response.ModelResolveResult;
import top.egon.mario.agent.model.service.MarioModelFactory;
import top.egon.mario.agent.soul.config.AgentSoulProperties;
import top.egon.mario.agent.soul.service.model.AgentSoulEvolutionDecision;
import top.egon.mario.agent.soul.service.model.AgentSoulEvolutionInput;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Verifies SoulMD evolution model request attribution and JSON parsing.
 */
class DefaultAgentSoulEvolutionModelTests {

    @Test
    void disabledEvolutionReturnsNoUpdateWithoutResolvingModel() {
        CapturingModelFactory factory = new CapturingModelFactory(response("{}"));
        DefaultAgentSoulEvolutionModel model = new DefaultAgentSoulEvolutionModel(factory, new ObjectMapper(),
                new AgentSoulProperties(false, ModelProviderType.DASHSCOPE, "qwen-test", new BigDecimal("0.2"), 512));

        var decision = model.evaluateAndRewrite(input());

        assertThat(decision.shouldUpdate()).isFalse();
        assertThat(decision.reason()).contains("disabled");
        assertThat(factory.request).isNull();
    }

    @Test
    void validJsonDecisionUsesConfiguredModelAndAuditScenario() {
        String json = """
                {
                  "shouldUpdate": true,
                  "reason": "durable preference",
                  "changeSummary": "Captured concise-answer preference",
                  "updatedSoulMd": "# Soul\\n- Keep answers concise."
                }
                """;
        CapturingModelFactory factory = new CapturingModelFactory(response(json));
        DefaultAgentSoulEvolutionModel model = new DefaultAgentSoulEvolutionModel(factory, new ObjectMapper(),
                new AgentSoulProperties(true, ModelProviderType.DASHSCOPE, "qwen-test", new BigDecimal("0.2"), 512));

        var decision = model.evaluateAndRewrite(input());

        assertThat(decision.shouldUpdate()).isTrue();
        assertThat(decision.reason()).isEqualTo("durable preference");
        assertThat(decision.changeSummary()).isEqualTo("Captured concise-answer preference");
        assertThat(decision.updatedSoulMd()).contains("Keep answers concise");
        assertThat(decision.modelProvider()).isEqualTo("DASHSCOPE");
        assertThat(decision.modelName()).isEqualTo("qwen-test");
        assertThat(factory.request.context().scenario()).isEqualTo(ModelScenario.AGENT_SOUL_EVOLUTION);
        assertThat(factory.request.options().temperature()).isEqualByComparingTo(new BigDecimal("0.2"));
        assertThat(factory.request.options().maxTokens()).isEqualTo(512);
    }

    @Test
    void invalidJsonReturnsNoUpdate() {
        CapturingModelFactory factory = new CapturingModelFactory(response("not json"));
        DefaultAgentSoulEvolutionModel model = new DefaultAgentSoulEvolutionModel(factory, new ObjectMapper(),
                new AgentSoulProperties(true, ModelProviderType.DASHSCOPE, "qwen-test", new BigDecimal("0.2"), 512));

        var decision = model.evaluateAndRewrite(input());

        assertThat(decision.shouldUpdate()).isFalse();
        assertThat(decision.reason()).contains("Invalid SoulMD evolution JSON");
    }

    @Test
    void nullJsonDecisionReturnsNoUpdate() {
        CapturingModelFactory factory = new CapturingModelFactory(response("null"));
        DefaultAgentSoulEvolutionModel model = new DefaultAgentSoulEvolutionModel(factory, new ObjectMapper(),
                new AgentSoulProperties(true, ModelProviderType.DASHSCOPE, "qwen-test", new BigDecimal("0.2"), 512));

        AgentSoulEvolutionDecision decision = assertDoesNotThrow(() -> model.evaluateAndRewrite(input()));

        assertThat(decision.shouldUpdate()).isFalse();
        assertThat(decision.reason()).contains("null decision");
    }

    private AgentSoulEvolutionInput input() {
        return new AgentSoulEvolutionInput(8L, "luigi", "# Current Soul", "remember concise answers",
                "I will keep answers concise", "recent context", "session-1", "request-1", "trace-1");
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static final class CapturingModelFactory implements MarioModelFactory {

        private final ChatResponse response;
        private ModelRequest request;

        private CapturingModelFactory(ChatResponse response) {
            this.response = response;
        }

        @Override
        public ModelResolveResult resolve(ModelRequest request) {
            this.request = request;
            return new ModelResolveResult(new StubChatModel(response), request.provider(), request.model(),
                    request.options(), request.context(), null);
        }
    }

    private static final class StubChatModel implements ChatModel {

        private final ChatResponse response;

        private StubChatModel(ChatResponse response) {
            this.response = response;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            return response;
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.empty();
        }
    }
}
