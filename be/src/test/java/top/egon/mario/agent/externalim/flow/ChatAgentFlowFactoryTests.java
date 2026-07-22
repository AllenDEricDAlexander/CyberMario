package top.egon.mario.agent.externalim.flow;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import top.egon.mario.agent.externalim.guard.ChatGuardResult;
import top.egon.mario.agent.externalim.guard.ChatGuardService;
import top.egon.mario.agent.externalim.model.ChatInvocation;
import top.egon.mario.agent.externalim.model.ChatSource;
import top.egon.mario.agent.externalim.model.ExternalChatPlatform;
import top.egon.mario.agent.externalim.model.ExternalConversationType;
import top.egon.mario.agent.externalim.model.ExternalMessageType;
import top.egon.mario.agent.externalim.model.ExternalSender;
import top.egon.mario.agent.externalim.model.ExternalSenderType;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ChatAgentFlowFactoryTests {

    private final ChatGuardService guardService = mock(ChatGuardService.class);
    private final DefaultChatAgentFlowFactory factory =
            new DefaultChatAgentFlowFactory(guardService);
    private final ChatInvocation invocation = new ChatInvocation(
            ChatSource.EXTERNAL_IM, "hello", 8L, null, null, "space-1",
            ExternalChatPlatform.TELEGRAM, "main", "-1001",
            ExternalConversationType.GROUP, "telegram:main:-1001",
            new ExternalSender("42", "Alice", ExternalSenderType.HUMAN),
            ExternalMessageType.TEXT, false, false, "update-1", "77",
            Instant.parse("2026-07-20T00:00:00Z"));

    @Test
    void replyDecisionRunsTheNestedReactAgentAndForwardsItsStream() {
        given(guardService.decide(eq(invocation), eq("group window"),
                eq("request-1"), eq("trace-1")))
                .willReturn(CompletableFuture.completedFuture(ChatGuardResult.reply("mentioned")));
        AtomicInteger modelCalls = new AtomicInteger();
        ReactAgent agent = agent(modelCalls);

        Flux<NodeOutput> output = factory.stream(invocation, agent,
                RunnableConfig.builder().threadId("__external_im__:space-1").build(),
                "group window", "request-1", "trace-1");

        StepVerifier.create(output.collectList())
                .assertNext(outputs -> {
                    assertThat(outputs).noneMatch(value ->
                            DefaultChatAgentFlowFactory.CHAT_GUARD.equals(value.node()));
                    assertThat(outputs.stream().map(this::text)
                            .filter(value -> !value.isBlank()).toList())
                            .containsSubsequence("first", "second");
                })
                .verifyComplete();
        assertThat(modelCalls).hasValue(1);
    }

    @Test
    void ignoreDecisionEndsWithoutCallingTheNestedReactAgent() {
        given(guardService.decide(eq(invocation), eq("group window"),
                eq("request-1"), eq("trace-1")))
                .willReturn(CompletableFuture.completedFuture(ChatGuardResult.ignore("ambient")));
        AtomicInteger modelCalls = new AtomicInteger();

        StepVerifier.create(factory.stream(invocation, agent(modelCalls),
                        RunnableConfig.builder().threadId("__external_im__:space-1").build(),
                        "group window", "request-1", "trace-1"))
                .verifyComplete();

        assertThat(modelCalls).hasValue(0);
    }

    private ReactAgent agent(AtomicInteger modelCalls) {
        return ReactAgent.builder()
                .name(DefaultChatAgentFlowFactory.CHAT_AGENT)
                .model(new StreamingStubChatModel(modelCalls))
                .tools(List.of())
                .build();
    }

    private String text(NodeOutput output) {
        if (output instanceof StreamingOutput<?> streaming
                && streaming.message() != null) {
            return streaming.message().getText();
        }
        return "";
    }

    private static final class StreamingStubChatModel implements ChatModel {

        private final AtomicInteger calls;

        private StreamingStubChatModel(AtomicInteger calls) {
            this.calls = calls;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            calls.incrementAndGet();
            return response("firstsecond");
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            calls.incrementAndGet();
            return Flux.just(response("first"), response("second"));
        }

        private ChatResponse response(String text) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
        }
    }
}
