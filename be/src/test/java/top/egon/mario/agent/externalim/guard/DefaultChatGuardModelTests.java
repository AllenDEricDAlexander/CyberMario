package top.egon.mario.agent.externalim.guard;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import top.egon.mario.agent.externalim.ExternalChatException;
import top.egon.mario.agent.externalim.guard.impl.DefaultChatGuardModel;
import top.egon.mario.agent.externalim.model.ChatInvocation;
import top.egon.mario.agent.externalim.model.ChatSource;
import top.egon.mario.agent.externalim.model.ExternalChatPlatform;
import top.egon.mario.agent.externalim.model.ExternalConversationType;
import top.egon.mario.agent.externalim.model.ExternalMessageType;
import top.egon.mario.agent.externalim.model.ExternalSender;
import top.egon.mario.agent.externalim.model.ExternalSenderType;
import top.egon.mario.agent.model.dto.enums.ModelProviderType;
import top.egon.mario.agent.model.dto.enums.ModelScenario;
import top.egon.mario.agent.model.dto.request.ModelRequest;
import top.egon.mario.agent.model.dto.response.ModelResolveResult;
import top.egon.mario.agent.model.service.MarioModelFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultChatGuardModelTests {

    @Test
    void parsesOnlyStrictDecisionJson() {
        CapturingModelFactory factory = new CapturingModelFactory("""
                {"decision":"REPLY","confidence":0.92,"reason":"direct question"}
                """);
        DefaultChatGuardModel model = model(factory);

        ChatGuardResult result = model.evaluate(input());

        assertThat(result.decision()).isEqualTo(ChatGuardDecision.REPLY);
        assertThat(result.confidence()).isEqualByComparingTo("0.92");
        assertThat(factory.request.context().scenario()).isEqualTo(ModelScenario.CHAT_GUARD);
    }

    @Test
    void rejectsUnknownDecisionOutOfRangeConfidenceAndTrailingText() {
        DefaultChatGuardModel model = model(new CapturingModelFactory("""
                {"decision":"MAYBE","confidence":1.2,"reason":"bad"} trailing
                """));

        assertThatThrownBy(() -> model.evaluate(input()))
                .isInstanceOf(ExternalChatException.class)
                .extracting(error -> ((ExternalChatException) error).code())
                .isEqualTo("CHAT_GUARD_RESPONSE_INVALID");
    }

    private DefaultChatGuardModel model(MarioModelFactory factory) {
        return new DefaultChatGuardModel(factory, new ObjectMapper(), new ChatGuardProperties(
                ModelProviderType.DASHSCOPE, "guard-model", BigDecimal.ZERO, 256,
                new BigDecimal("0.85"), Duration.ofSeconds(1)));
    }

    private ChatGuardModelInput input() {
        ChatInvocation invocation = new ChatInvocation(ChatSource.EXTERNAL_IM, "hello", 8L,
                null, null, "space-1", ExternalChatPlatform.TELEGRAM, "main", "-1001",
                ExternalConversationType.GROUP, "telegram:main:-1001",
                new ExternalSender("42", "Alice", ExternalSenderType.HUMAN),
                ExternalMessageType.TEXT, false, false, "update-1", "77", Instant.now());
        return new ChatGuardModelInput(invocation, "recent group", "request-1", "trace-1");
    }

    private static final class CapturingModelFactory implements MarioModelFactory {

        private final String response;
        private ModelRequest request;

        private CapturingModelFactory(String response) {
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

        private final String response;

        private StubChatModel(String response) {
            this.response = response;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(response))));
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.empty();
        }
    }
}
