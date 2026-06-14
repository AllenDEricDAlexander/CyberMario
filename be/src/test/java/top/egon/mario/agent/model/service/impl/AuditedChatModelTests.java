package top.egon.mario.agent.model.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import top.egon.mario.agent.model.dto.enums.ModelProviderType;
import top.egon.mario.agent.model.dto.enums.ModelScenario;
import top.egon.mario.agent.model.dto.request.ModelOptions;
import top.egon.mario.agent.model.po.enums.ModelAuditStatus;
import top.egon.mario.agent.model.po.enums.TokenUsageSource;
import top.egon.mario.agent.model.service.ModelAuditService;
import top.egon.mario.agent.model.service.model.ModelAuditEvent;
import top.egon.mario.agent.model.service.model.ModelCallContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies model call audit data collected by the ChatModel decorator.
 */
class AuditedChatModelTests {

    @Test
    void callRecordsSuccessfulProviderUsage() {
        CapturingModelAuditService auditService = new CapturingModelAuditService();
        ModelOptions options = new ModelOptions(new BigDecimal("0.6"), 128, null, null,
                true, null, null, null, Map.of());
        ModelCallContext context = context();
        AuditedChatModel model = new AuditedChatModel(
                new StubChatModel(response("hello", 10, 5, 15), null),
                auditService,
                ModelProviderType.DASHSCOPE,
                "qwen-plus",
                options,
                context
        );

        ChatResponse response = model.call(prompt());

        assertThat(response.getResult().getOutput().getText()).isEqualTo("hello");
        assertThat(auditService.events).hasSize(1);
        ModelAuditEvent event = auditService.events.getFirst();
        assertThat(event.requestId()).isEqualTo("request-1");
        assertThat(event.traceId()).isEqualTo("trace-1");
        assertThat(event.userId()).isEqualTo(7L);
        assertThat(event.sessionId()).isEqualTo("session-1");
        assertThat(event.threadId()).isEqualTo("thread-1");
        assertThat(event.scenario()).isEqualTo(ModelScenario.RAG_CHAT);
        assertThat(event.provider()).isEqualTo(ModelProviderType.DASHSCOPE);
        assertThat(event.model()).isEqualTo("qwen-plus");
        assertThat(event.options()).isSameAs(options);
        assertThat(event.streaming()).isFalse();
        assertThat(event.status()).isEqualTo(ModelAuditStatus.SUCCESS);
        assertThat(event.tokenUsage().promptTokens()).isEqualTo(10);
        assertThat(event.tokenUsage().completionTokens()).isEqualTo(5);
        assertThat(event.tokenUsage().totalTokens()).isEqualTo(15);
        assertThat(event.tokenUsage().source()).isEqualTo(TokenUsageSource.PROVIDER);
        assertThat(event.promptChars()).isEqualTo("systemuser".length());
        assertThat(event.completionChars()).isEqualTo("hello".length());
        assertThat(event.errorCode()).isNull();
        assertThat(event.errorMessage()).isNull();
        assertThat(event.durationMs()).isNotNegative();
    }

    @Test
    void callRecordsFailure() {
        CapturingModelAuditService auditService = new CapturingModelAuditService();
        RuntimeException failure = new IllegalStateException("provider failed");
        AuditedChatModel model = new AuditedChatModel(
                new StubChatModel(null, failure),
                auditService,
                ModelProviderType.DASHSCOPE,
                "qwen-plus",
                null,
                context()
        );

        assertThatThrownBy(() -> model.call(prompt()))
                .isSameAs(failure);

        assertThat(auditService.events).hasSize(1);
        ModelAuditEvent event = auditService.events.getFirst();
        assertThat(event.status()).isEqualTo(ModelAuditStatus.FAILED);
        assertThat(event.tokenUsage().source()).isEqualTo(TokenUsageSource.UNAVAILABLE);
        assertThat(event.errorCode()).isEqualTo(IllegalStateException.class.getName());
        assertThat(event.errorMessage()).isEqualTo("provider failed");
        assertThat(event.promptChars()).isEqualTo("systemuser".length());
        assertThat(event.completionChars()).isZero();
    }

    @Test
    void streamRecordsSuccessfulProviderUsageFromLastChunk() {
        CapturingModelAuditService auditService = new CapturingModelAuditService();
        AuditedChatModel model = new AuditedChatModel(
                new StubChatModel(Flux.just(
                        response("hel", null, null, null),
                        response("lo", 10, 5, 15)
                )),
                auditService,
                ModelProviderType.DASHSCOPE,
                "qwen-plus",
                null,
                context()
        );

        StepVerifier.create(model.stream(prompt()).map(item -> item.getResult().getOutput().getText()))
                .expectNext("hel", "lo")
                .verifyComplete();

        assertThat(auditService.events).hasSize(1);
        ModelAuditEvent event = auditService.events.getFirst();
        assertThat(event.streaming()).isTrue();
        assertThat(event.status()).isEqualTo(ModelAuditStatus.SUCCESS);
        assertThat(event.tokenUsage().promptTokens()).isEqualTo(10);
        assertThat(event.tokenUsage().completionTokens()).isEqualTo(5);
        assertThat(event.tokenUsage().totalTokens()).isEqualTo(15);
        assertThat(event.tokenUsage().source()).isEqualTo(TokenUsageSource.PROVIDER);
        assertThat(event.completionChars()).isEqualTo("hello".length());
    }

    @Test
    void streamRecordsFailure() {
        CapturingModelAuditService auditService = new CapturingModelAuditService();
        RuntimeException failure = new IllegalArgumentException("stream failed");
        AuditedChatModel model = new AuditedChatModel(
                new StubChatModel(Flux.concat(Flux.just(response("hel", null, null, null)), Flux.error(failure))),
                auditService,
                ModelProviderType.DASHSCOPE,
                "qwen-plus",
                null,
                context()
        );

        StepVerifier.create(model.stream(prompt()))
                .expectNextCount(1)
                .expectErrorMatches(error -> error == failure)
                .verify();

        assertThat(auditService.events).hasSize(1);
        ModelAuditEvent event = auditService.events.getFirst();
        assertThat(event.streaming()).isTrue();
        assertThat(event.status()).isEqualTo(ModelAuditStatus.FAILED);
        assertThat(event.tokenUsage().source()).isEqualTo(TokenUsageSource.UNAVAILABLE);
        assertThat(event.errorCode()).isEqualTo(IllegalArgumentException.class.getName());
        assertThat(event.errorMessage()).isEqualTo("stream failed");
        assertThat(event.completionChars()).isEqualTo("hel".length());
    }

    private Prompt prompt() {
        return new Prompt(List.of(new SystemMessage("system"), new UserMessage("user")));
    }

    private ModelCallContext context() {
        return new ModelCallContext(7L, "trace-1", "session-1", "thread-1",
                ModelScenario.RAG_CHAT, "request-1", "127.0.0.1", "JUnit");
    }

    private ChatResponse response(String text, Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        ChatResponseMetadata.Builder metadata = ChatResponseMetadata.builder().model("qwen-plus");
        if (promptTokens != null || completionTokens != null || totalTokens != null) {
            metadata.usage(new DefaultUsage(promptTokens, completionTokens, totalTokens));
        }
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))), metadata.build());
    }

    private static final class CapturingModelAuditService implements ModelAuditService {

        private final List<ModelAuditEvent> events = new ArrayList<>();

        @Override
        public void record(ModelAuditEvent event) {
            events.add(event);
        }
    }

    private static final class StubChatModel implements ChatModel {

        private final ChatResponse response;
        private final RuntimeException failure;
        private final Flux<ChatResponse> stream;

        private StubChatModel(ChatResponse response, RuntimeException failure) {
            this.response = response;
            this.failure = failure;
            this.stream = Flux.empty();
        }

        private StubChatModel(Flux<ChatResponse> stream) {
            this.response = null;
            this.failure = null;
            this.stream = stream;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            if (failure != null) {
                throw failure;
            }
            return response;
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return stream;
        }

        @Override
        public ChatOptions getDefaultOptions() {
            return ChatOptions.builder().build();
        }
    }

}
