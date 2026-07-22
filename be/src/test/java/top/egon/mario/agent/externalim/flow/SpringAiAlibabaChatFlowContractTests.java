package top.egon.mario.agent.externalim.flow;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategyFactoryBuilder;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static org.assertj.core.api.Assertions.assertThat;

class SpringAiAlibabaChatFlowContractTests {

    private static final String GUARD = "chat_guard";
    private static final String AGENT = "chat_agent";
    private static final String DECISION = "guardDecision";

    @Test
    void replyEdgeForwardsNestedAgentStreamingWithoutBuffering() throws GraphStateException {
        AtomicInteger modelCalls = new AtomicInteger();
        ReactAgent agent = ReactAgent.builder()
                .name(AGENT)
                .model(new StreamingStubChatModel(modelCalls))
                .tools(List.of())
                .build();
        CompiledGraph graph = graph("REPLY", agent);

        Flux<NodeOutput> output = graph.stream(
                Map.of("messages", List.of(new UserMessage("hello")), "input", "hello"),
                RunnableConfig.builder().threadId("contract-reply").build());

        StepVerifier.create(output)
                .recordWith(java.util.ArrayList::new)
                .thenConsumeWhile(ignored -> true)
                .consumeRecordedWith(values -> assertThat(values)
                        .anySatisfy(value -> assertThat(value.toString()).contains("first"))
                        .anySatisfy(value -> assertThat(value.toString()).contains("second")))
                .verifyComplete();
        assertThat(modelCalls).hasValue(1);
    }

    @Test
    void ignoreEdgeEndsWithoutCallingNestedAgent() throws GraphStateException {
        AtomicInteger modelCalls = new AtomicInteger();
        ReactAgent agent = ReactAgent.builder()
                .name(AGENT)
                .model(new StreamingStubChatModel(modelCalls))
                .tools(List.of())
                .build();

        StepVerifier.create(graph("IGNORE", agent).stream(
                        Map.of("messages", List.of(new UserMessage("ambient group chat")), "input", "ambient group chat"),
                        RunnableConfig.builder().threadId("contract-ignore").build()))
                .thenConsumeWhile(ignored -> true)
                .verifyComplete();

        assertThat(modelCalls).hasValue(0);
    }

    private CompiledGraph graph(String decision, ReactAgent agent) throws GraphStateException {
        return new StateGraph("contract_flow", new KeyStrategyFactoryBuilder().build())
                .addNode(GUARD, state -> CompletableFuture.completedFuture(Map.of(DECISION, decision)))
                .addNode(AGENT, agent.asNode(true, true))
                .addEdge(START, GUARD)
                .addConditionalEdges(GUARD,
                        state -> CompletableFuture.completedFuture(state.value(DECISION, "IGNORE")),
                        Map.of("REPLY", AGENT, "IGNORE", END))
                .addEdge(AGENT, END)
                .compile();
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
