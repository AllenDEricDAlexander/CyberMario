package top.egon.mario.agent.externalim.flow;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategyFactoryBuilder;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import top.egon.mario.agent.externalim.guard.ChatGuardDecision;
import top.egon.mario.agent.externalim.guard.ChatGuardService;
import top.egon.mario.agent.externalim.model.ChatInvocation;
import top.egon.mario.agent.service.AgentException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class DefaultChatAgentFlowFactory implements ChatAgentFlowFactory {

    static final String CHAT_GUARD = "chat_guard";
    static final String CHAT_AGENT = "chat_agent";
    static final String GUARD_DECISION = "guardDecision";

    private final ChatGuardService guardService;

    public DefaultChatAgentFlowFactory(ChatGuardService guardService) {
        this.guardService = guardService;
    }

    @Override
    public Flux<NodeOutput> stream(ChatInvocation invocation, ReactAgent agent,
                                   RunnableConfig config, String guardGroupWindow,
                                   String requestId, String traceId) {
        if (invocation == null || agent == null || config == null) {
            return Flux.error(new AgentException("AGENT_CHAT_FLOW_INVALID",
                    "chat flow invocation, agent and config are required"));
        }
        try {
            CompiledGraph graph = new StateGraph("chat_guard_flow",
                    new KeyStrategyFactoryBuilder().build())
                    .addNode(CHAT_GUARD, state -> guardService
                            .decide(invocation, guardGroupWindow, requestId, traceId)
                            .thenApply(result -> Map.of(
                                    GUARD_DECISION, result.decision().name())))
                    .addNode(CHAT_AGENT, agent.asNode(true, true))
                    .addEdge(StateGraph.START, CHAT_GUARD)
                    .addConditionalEdges(CHAT_GUARD,
                            state -> CompletableFuture.completedFuture(
                                    state.value(GUARD_DECISION, ChatGuardDecision.IGNORE.name())),
                            Map.of(
                                    ChatGuardDecision.REPLY.name(), CHAT_AGENT,
                                    ChatGuardDecision.IGNORE.name(), StateGraph.END))
                    .addEdge(CHAT_AGENT, StateGraph.END)
                    .compile();
            return graph.stream(Map.of(
                            "messages", List.of(new UserMessage(invocation.message())),
                            "input", invocation.message()), config)
                    .filter(output -> !StateGraph.START.equals(output.node()))
                    .filter(output -> !CHAT_GUARD.equals(output.node()))
                    .filter(output -> !StateGraph.END.equals(output.node()));
        } catch (GraphStateException error) {
            return Flux.error(new AgentException("AGENT_CHAT_FLOW_INVALID",
                    "chat guard graph cannot be compiled"));
        }
    }
}
