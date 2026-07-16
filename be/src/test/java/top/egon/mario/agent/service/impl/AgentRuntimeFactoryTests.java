package top.egon.mario.agent.service.impl;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.alibaba.cloud.ai.graph.agent.interceptor.Interceptor;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import reactor.core.publisher.Flux;
import top.egon.mario.agent.mcp.runtime.McpAgentToolProvider;
import top.egon.mario.agent.mcp.runtime.LoggingMcpToolCallback;
import top.egon.mario.agent.model.dto.enums.ModelProviderType;
import top.egon.mario.agent.model.dto.enums.ModelScenario;
import top.egon.mario.agent.model.dto.request.ModelOptions;
import top.egon.mario.agent.model.dto.request.ModelRequest;
import top.egon.mario.agent.model.dto.response.ModelResolveResult;
import top.egon.mario.agent.model.service.MarioModelFactory;
import top.egon.mario.agent.model.service.model.ModelCallContext;
import top.egon.mario.agent.memory.checkpoint.AgentMemoryCheckpointerProvider;
import top.egon.mario.agent.memory.hook.AgentMemoryMessagesHook;
import top.egon.mario.agent.observability.interceptor.AgentObservabilityModelInterceptor;
import top.egon.mario.agent.observability.interceptor.AgentObservabilityToolInterceptor;
import top.egon.mario.agent.observability.po.enums.AgentRunToolType;
import top.egon.mario.agent.service.AgentRuntimeFactory;
import top.egon.mario.agent.service.model.AgentModelConfig;
import top.egon.mario.agent.service.model.AgentOptions;
import top.egon.mario.agent.service.model.AgentRuntimeSpec;
import top.egon.mario.agent.service.model.AgentToolConfig;
import top.egon.mario.agent.service.model.ScopedAgentToolSet;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Verifies dynamic ReactAgent runtime construction from resolved specs.
 */
class AgentRuntimeFactoryTests {

    @Test
    void getResolvesModelAndBuildsAgentWithEnabledTools() {
        StubMarioModelFactory modelFactory = new StubMarioModelFactory();
        StubAgentBuilder builder = new StubAgentBuilder();
        ToolCallback wikipedia = tool("searchWikipedia");
        ToolCallback duckduckgo = tool("searchDuckDuckGoNews");
        DefaultAgentRuntimeFactory factory = new DefaultAgentRuntimeFactory(
                modelFactory,
                List.of(wikipedia, duckduckgo),
                builder
        );
        ModelOptions options = new ModelOptions(new BigDecimal("0.4"), 2048, new BigDecimal("0.8"),
                null, true, null, null, true, Map.of());
        AgentRuntimeSpec spec = new AgentRuntimeSpec(
                9L,
                new AgentModelConfig(ModelProviderType.DASHSCOPE, "qwen3.6-max-preview"),
                options,
                "system prompt",
                new AgentToolConfig(Set.of("searchWikipedia")),
                new AgentOptions(true, 2, 60),
                "fingerprint-1"
        );
        ModelCallContext context = new ModelCallContext(8L, "trace-1", null, "thread-1",
                ModelScenario.AGENT_CHAT, "request-1", null, null);

        ReactAgent agent = factory.get(spec, context);

        assertThat(agent).isSameAs(builder.agent);
        assertThat(modelFactory.request).isNotNull();
        assertThat(modelFactory.request.provider()).isEqualTo(ModelProviderType.DASHSCOPE);
        assertThat(modelFactory.request.model()).isEqualTo("qwen3.6-max-preview");
        assertThat(modelFactory.request.options()).isEqualTo(options);
        assertThat(modelFactory.request.context()).isEqualTo(context);
        assertThat(builder.name).isEqualTo("cyber_mario_agent");
        assertThat(builder.model).isSameAs(modelFactory.chatModel);
        assertThat(builder.chatOptions.getModel()).isEqualTo("qwen3.6-max-preview");
        assertThat(builder.systemPrompt).isEqualTo("system prompt");
        assertThat(builder.tools).containsExactly(wikipedia);
        assertThat(builder.parallelToolExecution).isTrue();
        assertThat(builder.maxParallelTools).isEqualTo(2);
        assertThat(builder.toolExecutionTimeoutSeconds).isEqualTo(60);
    }

    @Test
    void getRebuildsAgentsForEachCallToKeepModelAuditContextFresh() {
        StubMarioModelFactory modelFactory = new StubMarioModelFactory();
        StubAgentBuilder builder = new StubAgentBuilder();
        DefaultAgentRuntimeFactory factory = new DefaultAgentRuntimeFactory(modelFactory, List.of(), builder);
        AgentRuntimeSpec spec = new AgentRuntimeSpec(null,
                new AgentModelConfig(ModelProviderType.DASHSCOPE, "qwen3.6-max-preview"),
                new ModelOptions(null, null, null, null, null, null, null, null, Map.of()),
                "system prompt",
                new AgentToolConfig(Set.of()),
                new AgentOptions(false, 5, 300),
                "fingerprint-1");
        ModelCallContext context = new ModelCallContext(8L, "trace-1", null, "thread-1",
                ModelScenario.AGENT_CHAT, "request-1", null, null);
        ModelCallContext nextContext = new ModelCallContext(9L, "trace-2", null, "thread-2",
                ModelScenario.AGENT_CHAT, "request-2", null, null);

        ReactAgent first = factory.get(spec, context);
        ReactAgent second = factory.get(spec, nextContext);

        assertThat(first).isNotSameAs(second);
        assertThat(modelFactory.resolveCount).isEqualTo(2);
        assertThat(builder.buildCount).isEqualTo(2);
        assertThat(modelFactory.request.context()).isEqualTo(nextContext);
    }

    @Test
    void getIncludesCurrentMcpToolSnapshotForEachCall() {
        StubMarioModelFactory modelFactory = new StubMarioModelFactory();
        StubAgentBuilder builder = new StubAgentBuilder();
        ToolCallback firstMcpTool = tool("mcp_one");
        ToolCallback secondMcpTool = tool("mcp_two");
        McpAgentToolProvider mcpToolProvider = mock(McpAgentToolProvider.class);
        given(mcpToolProvider.currentToolCallbacks()).willReturn(
                new ToolCallback[]{firstMcpTool},
                new ToolCallback[]{secondMcpTool}
        );
        DefaultAgentRuntimeFactory factory = new DefaultAgentRuntimeFactory(
                modelFactory,
                List.of(),
                builder,
                mcpToolProvider
        );
        ModelCallContext context = new ModelCallContext(8L, "trace-1", null, "thread-1",
                ModelScenario.AGENT_CHAT, "request-1", null, null);

        factory.get(specWithTools("mcp_one"), context);
        List<ToolCallback> firstTools = builder.tools;
        factory.get(specWithTools("mcp_two"), context);

        assertThat(firstTools).containsExactly(firstMcpTool);
        assertThat(builder.tools).containsExactly(secondMcpTool);
    }

    @Test
    void getPassesObservabilityInterceptorsToReactAgentBuilder() {
        StubMarioModelFactory modelFactory = new StubMarioModelFactory();
        StubAgentBuilder builder = new StubAgentBuilder();
        AgentObservabilityModelInterceptor modelInterceptor = mock(AgentObservabilityModelInterceptor.class);
        AgentObservabilityToolInterceptor toolInterceptor = mock(AgentObservabilityToolInterceptor.class);
        DefaultAgentRuntimeFactory factory = new DefaultAgentRuntimeFactory(
                modelFactory,
                List.of(),
                builder,
                null,
                List.of(modelInterceptor, toolInterceptor)
        );
        ModelCallContext context = new ModelCallContext(8L, "trace-1", null, "thread-1",
                ModelScenario.AGENT_CHAT, "request-1", null, null);

        factory.get(specWithTools(), context);

        assertThat(builder.interceptors)
                .anySatisfy(interceptor -> assertThat(interceptor).isSameAs(modelInterceptor))
                .anySatisfy(interceptor -> assertThat(interceptor).isSameAs(toolInterceptor));
    }

    @Test
    void getPassesMemoryHookAndCheckpointSaverToReactAgentBuilder() {
        StubMarioModelFactory modelFactory = new StubMarioModelFactory();
        StubAgentBuilder builder = new StubAgentBuilder();
        AgentMemoryMessagesHook memoryHook = new AgentMemoryMessagesHook();
        AgentMemoryCheckpointerProvider checkpointerProvider = mock(AgentMemoryCheckpointerProvider.class);
        BaseCheckpointSaver saver = mock(BaseCheckpointSaver.class);
        given(checkpointerProvider.saver()).willReturn(saver);
        DefaultAgentRuntimeFactory factory = new DefaultAgentRuntimeFactory(
                modelFactory,
                List.of(),
                builder,
                null,
                List.of(),
                checkpointerProvider,
                List.of(memoryHook)
        );
        ModelCallContext context = new ModelCallContext(8L, "trace-1", null, "thread-1",
                ModelScenario.AGENT_CHAT, "request-1", null, null);

        factory.get(specWithTools(), context);

        assertThat(builder.checkpointSaver).isSameAs(saver);
        assertThat(builder.hooks).contains(memoryHook);
    }

    @Test
    void toolDescriptorsExposeMcpServerIdentityForEnabledMcpTools() {
        StubMarioModelFactory modelFactory = new StubMarioModelFactory();
        StubAgentBuilder builder = new StubAgentBuilder();
        ToolCallback localTool = tool("searchWikipedia");
        LoggingMcpToolCallback mcpTool = mock(LoggingMcpToolCallback.class);
        given(mcpTool.getToolDefinition()).willReturn(org.springframework.ai.tool.definition.ToolDefinition.builder()
                .name("docs_search")
                .description("stub")
                .inputSchema("{}")
                .build());
        given(mcpTool.serverCode()).willReturn("docs");
        McpAgentToolProvider mcpToolProvider = mock(McpAgentToolProvider.class);
        given(mcpToolProvider.currentToolCallbacks()).willReturn(new ToolCallback[]{mcpTool});
        DefaultAgentRuntimeFactory factory = new DefaultAgentRuntimeFactory(
                modelFactory,
                List.of(localTool),
                builder,
                mcpToolProvider
        );

        Map<String, top.egon.mario.agent.observability.service.model.AgentRunAuditContext.ToolDescriptor> descriptors =
                factory.toolDescriptors(specWithTools("searchWikipedia", "docs_search"));

        assertThat(descriptors).containsOnlyKeys("searchWikipedia", "docs_search");
        assertThat(descriptors.get("searchWikipedia").toolType()).isEqualTo(AgentRunToolType.LOCAL);
        assertThat(descriptors.get("docs_search").toolType()).isEqualTo(AgentRunToolType.MCP);
        assertThat(descriptors.get("docs_search").mcpServerCode()).isEqualTo("docs");
    }

    @Test
    void runtimeUsesSameMcpSnapshotForAgentToolsAndAuditDescriptors() {
        StubMarioModelFactory modelFactory = new StubMarioModelFactory();
        StubAgentBuilder builder = new StubAgentBuilder();
        LoggingMcpToolCallback firstMcpTool = mock(LoggingMcpToolCallback.class);
        given(firstMcpTool.getToolDefinition()).willReturn(org.springframework.ai.tool.definition.ToolDefinition.builder()
                .name("docs_search")
                .description("stub")
                .inputSchema("{}")
                .build());
        given(firstMcpTool.serverCode()).willReturn("docs-one");
        LoggingMcpToolCallback secondMcpTool = mock(LoggingMcpToolCallback.class);
        given(secondMcpTool.getToolDefinition()).willReturn(org.springframework.ai.tool.definition.ToolDefinition.builder()
                .name("docs_search")
                .description("stub")
                .inputSchema("{}")
                .build());
        given(secondMcpTool.serverCode()).willReturn("docs-two");
        McpAgentToolProvider mcpToolProvider = mock(McpAgentToolProvider.class);
        given(mcpToolProvider.currentToolCallbacks()).willReturn(
                new ToolCallback[]{firstMcpTool},
                new ToolCallback[]{secondMcpTool}
        );
        DefaultAgentRuntimeFactory factory = new DefaultAgentRuntimeFactory(
                modelFactory,
                List.of(),
                builder,
                mcpToolProvider
        );
        ModelCallContext context = new ModelCallContext(8L, "trace-1", null, "thread-1",
                ModelScenario.AGENT_CHAT, "request-1", null, null);

        AgentRuntimeFactory.AgentRuntime runtime = factory.runtime(specWithTools("docs_search"), context);

        assertThat(builder.tools).containsExactly(firstMcpTool);
        assertThat(runtime.toolDescriptors().get("docs_search").mcpServerCode()).isEqualTo("docs-one");
    }

    @Test
    void localToolWinsWhenMcpToolNameConflicts() {
        StubMarioModelFactory modelFactory = new StubMarioModelFactory();
        StubAgentBuilder builder = new StubAgentBuilder();
        ToolCallback localTool = tool("searchWikipedia");
        ToolCallback mcpTool = tool("searchWikipedia");
        McpAgentToolProvider mcpToolProvider = mock(McpAgentToolProvider.class);
        given(mcpToolProvider.currentToolCallbacks()).willReturn(new ToolCallback[]{mcpTool});
        DefaultAgentRuntimeFactory factory = new DefaultAgentRuntimeFactory(
                modelFactory,
                List.of(localTool),
                builder,
                mcpToolProvider
        );
        ModelCallContext context = new ModelCallContext(8L, "trace-1", null, "thread-1",
                ModelScenario.AGENT_CHAT, "request-1", null, null);

        factory.get(specWithTools("searchWikipedia"), context);

        assertThat(builder.tools).containsExactly(localTool);
    }

    @Test
    void scopedToolNameCannotCollideWithCurrentMcpSnapshot() {
        StubMarioModelFactory modelFactory = new StubMarioModelFactory();
        StubAgentBuilder builder = new StubAgentBuilder();
        McpAgentToolProvider mcpToolProvider = mock(McpAgentToolProvider.class);
        ToolCallback mcpTool = tool("get_investment_portfolio");
        given(mcpToolProvider.currentToolCallbacks()).willReturn(new ToolCallback[]{mcpTool});
        DefaultAgentRuntimeFactory factory = new DefaultAgentRuntimeFactory(
                modelFactory, List.of(), builder, mcpToolProvider);
        ModelCallContext context = new ModelCallContext(8L, "trace-1", null, "thread-1",
                ModelScenario.AGENT_CHAT, "request-1", null, null);

        assertThatThrownBy(() -> factory.get(specWithTools(), context,
                ScopedAgentToolSet.readOnly(tool("get_investment_portfolio"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conflicts with a default tool");
    }

    @Test
    void scopedToolsAreAvailableOnlyToOneExplicitRuntime() {
        StubMarioModelFactory modelFactory = new StubMarioModelFactory();
        StubAgentBuilder builder = new StubAgentBuilder();
        ToolCallback defaultTool = tool("searchWikipedia");
        ToolCallback scopedTool = tool("get_investment_portfolio");
        DefaultAgentRuntimeFactory factory = new DefaultAgentRuntimeFactory(
                modelFactory, List.of(defaultTool), builder);
        AgentRuntimeSpec spec = specWithTools("searchWikipedia", "get_investment_portfolio");
        ModelCallContext context = new ModelCallContext(8L, "trace-1", null, "thread-1",
                ModelScenario.INVESTMENT_AGENT, "request-1", null, null);

        AgentRuntimeFactory.AgentRuntime scopedRuntime = factory.runtime(
                spec, context, ScopedAgentToolSet.readOnly(scopedTool));
        List<ToolCallback> scopedCallbacks = builder.tools;
        factory.runtime(spec, context);

        assertThat(scopedCallbacks).containsExactly(defaultTool, scopedTool);
        assertThat(scopedRuntime.toolDescriptors()).containsOnlyKeys(
                "searchWikipedia", "get_investment_portfolio");
        assertThat(builder.tools).containsExactly(defaultTool);
        assertThat(factory.toolDescriptors(spec)).containsOnlyKeys("searchWikipedia");
        assertThat(builder.parallelToolExecution).isFalse();
    }

    @Test
    void scopedCallbackIsDispatchedOnlyByItsRealRuntime() throws Exception {
        AtomicInteger callbackCalls = new AtomicInteger();
        ToolCallback scopedTool = callback("get_investment_portfolio", input -> {
            callbackCalls.incrementAndGet();
            return "private portfolio";
        });
        ToolDispatchChatModel chatModel = new ToolDispatchChatModel("get_investment_portfolio");
        DefaultAgentRuntimeFactory factory = new DefaultAgentRuntimeFactory(
                new StubMarioModelFactory(chatModel), List.of(), new RealAgentBuilder());
        AgentRuntimeSpec spec = specWithTools("get_investment_portfolio");
        ModelCallContext context = new ModelCallContext(8L, "trace-1", null, "thread-1",
                ModelScenario.INVESTMENT_AGENT, "request-1", null, null);

        AgentRuntimeFactory.AgentRuntime scopedRuntime = factory.runtime(
                spec, context, ScopedAgentToolSet.readOnly(scopedTool));
        AgentRuntimeFactory.AgentRuntime ordinaryRuntime = factory.runtime(spec, context);

        assertThat(scopedRuntime.agent()).isNotSameAs(ordinaryRuntime.agent());
        assertThat(scopedRuntime.toolDescriptors()).containsOnlyKeys("get_investment_portfolio");
        assertThat(ordinaryRuntime.toolDescriptors()).isEmpty();
        assertThat(factory.toolDescriptors(spec)).isEmpty();
        assertThat(scopedRuntime.agent().call("inspect portfolio", nonStreaming("scoped-run")).getText())
                .isEqualTo("model observed: private portfolio");
        assertThat(ordinaryRuntime.agent().call("inspect portfolio", nonStreaming("ordinary-run")).getText())
                .isEqualTo("tool unavailable");
        assertThat(callbackCalls).hasValue(1);
        assertThat(chatModel.toolSnapshots()).containsExactly(
                List.of("get_investment_portfolio"),
                List.of("get_investment_portfolio"),
                List.of()
        );
    }

    @Test
    void scopedCallbackFailureReachesModelThroughRealDispatch() throws Exception {
        IllegalStateException failure = new IllegalStateException("portfolio unavailable");
        ToolCallback scopedTool = callback("get_investment_portfolio", input -> {
            throw failure;
        });
        ToolDispatchChatModel chatModel = new ToolDispatchChatModel("get_investment_portfolio");
        DefaultAgentRuntimeFactory factory = new DefaultAgentRuntimeFactory(
                new StubMarioModelFactory(chatModel), List.of(), new RealAgentBuilder());
        ModelCallContext context = new ModelCallContext(8L, "trace-1", null, "thread-1",
                ModelScenario.INVESTMENT_AGENT, "request-1", null, null);

        ReactAgent agent = factory.get(specWithTools("get_investment_portfolio"), context,
                ScopedAgentToolSet.readOnly(scopedTool));

        assertThat(agent.call("inspect portfolio", nonStreaming("failed-run")).getText())
                .isEqualTo("model observed: Error: portfolio unavailable");
        assertThat(chatModel.toolResponses()).containsExactly("Error: portfolio unavailable");
    }

    @Test
    void scopedToolNameCannotCollideWithAnyDefaultTool() {
        StubMarioModelFactory modelFactory = new StubMarioModelFactory();
        StubAgentBuilder builder = new StubAgentBuilder();
        DefaultAgentRuntimeFactory factory = new DefaultAgentRuntimeFactory(
                modelFactory, List.of(tool("get_investment_portfolio")), builder);
        ModelCallContext context = new ModelCallContext(8L, "trace-1", null, "thread-1",
                ModelScenario.INVESTMENT_AGENT, "request-1", null, null);

        assertThatThrownBy(() -> factory.runtime(specWithTools(), context,
                ScopedAgentToolSet.readOnly(tool("get_investment_portfolio"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conflicts with a default tool");
    }

    @Test
    void scopedCallbackExceptionIsNotWrappedByRuntimeFactory() {
        StubAgentBuilder builder = new StubAgentBuilder();
        ToolCallback scopedTool = tool("get_investment_portfolio");
        IllegalStateException failure = new IllegalStateException("portfolio unavailable");
        given(scopedTool.call("{}")).willThrow(failure);
        DefaultAgentRuntimeFactory factory = new DefaultAgentRuntimeFactory(
                new StubMarioModelFactory(), List.of(), builder);
        ModelCallContext context = new ModelCallContext(8L, "trace-1", null, "thread-1",
                ModelScenario.INVESTMENT_AGENT, "request-1", null, null);

        factory.get(specWithTools(), context, ScopedAgentToolSet.readOnly(scopedTool));

        assertThatThrownBy(() -> builder.tools.get(0).call("{}"))
                .isSameAs(failure);
    }

    @Test
    void legacyFactoryOverloadsRemainCompatible() {
        ReactAgent agent = mock(ReactAgent.class);
        AgentRuntimeFactory legacyFactory = (spec, context) -> agent;
        AgentRuntimeSpec spec = specWithTools();
        ModelCallContext context = new ModelCallContext(8L, "trace-1", null, "thread-1",
                ModelScenario.AGENT_CHAT, "request-1", null, null);

        assertThat(legacyFactory.get(spec, context)).isSameAs(agent);
        assertThat(legacyFactory.get(spec, context, ScopedAgentToolSet.empty())).isSameAs(agent);
        assertThat(legacyFactory.runtime(spec, context).agent()).isSameAs(agent);
        assertThat(legacyFactory.runtime(spec, context, ScopedAgentToolSet.empty())
                .agent()).isSameAs(agent);
        assertThatThrownBy(() -> legacyFactory.runtime(spec, context,
                ScopedAgentToolSet.readOnly(tool("investment_read"))))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("scoped agent tools");
    }

    private AgentRuntimeSpec specWithTools(String... toolNames) {
        return new AgentRuntimeSpec(null,
                new AgentModelConfig(ModelProviderType.DASHSCOPE, "qwen3.6-max-preview"),
                new ModelOptions(null, null, null, null, null, null, null, null, Map.of()),
                "system prompt",
                new AgentToolConfig(Set.of(toolNames)),
                new AgentOptions(false, 5, 300),
                "fingerprint-1");
    }

    private ToolCallback tool(String name) {
        ToolCallback tool = mock(ToolCallback.class);
        given(tool.getToolDefinition()).willReturn(org.springframework.ai.tool.definition.ToolDefinition.builder()
                .name(name)
                .description("stub")
                .inputSchema("{}")
                .build());
        return tool;
    }

    private ToolCallback callback(String name, java.util.function.Function<String, String> function) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name(name)
                        .description("runtime lifecycle test tool")
                        .inputSchema("{}")
                        .build();
            }

            @Override
            public String call(String toolInput) {
                return function.apply(toolInput);
            }
        };
    }

    private RunnableConfig nonStreaming(String threadId) {
        return RunnableConfig.builder()
                .threadId(threadId)
                .addMetadata("_stream_", false)
                .build();
    }

    private static final class StubMarioModelFactory implements MarioModelFactory {

        private final ChatModel chatModel;
        private ModelRequest request;
        private int resolveCount;

        private StubMarioModelFactory() {
            this(new StubChatModel());
        }

        private StubMarioModelFactory(ChatModel chatModel) {
            this.chatModel = chatModel;
        }

        @Override
        public ModelResolveResult resolve(ModelRequest request) {
            this.request = request;
            this.resolveCount++;
            return new ModelResolveResult(chatModel, request.provider(), request.model(), request.options(),
                    request.context(), ChatOptions.builder().model(request.model()).build());
        }
    }

    private static final class StubAgentBuilder implements DefaultAgentRuntimeFactory.AgentBuilder {

        private ReactAgent agent;
        private String name;
        private ChatModel model;
        private ChatOptions chatOptions;
        private String systemPrompt;
        private List<ToolCallback> tools = List.of();
        private List<Interceptor> interceptors = List.of();
        private List<Hook> hooks = List.of();
        private BaseCheckpointSaver checkpointSaver;
        private boolean parallelToolExecution;
        private int maxParallelTools;
        private int toolExecutionTimeoutSeconds;
        private int buildCount;

        @Override
        public ReactAgent build(DefaultAgentRuntimeFactory.AgentBuildRequest request) {
            this.agent = mock(ReactAgent.class);
            this.name = request.name();
            this.model = request.model();
            this.chatOptions = request.chatOptions();
            this.systemPrompt = request.systemPrompt();
            this.tools = request.tools();
            this.interceptors = request.interceptors();
            this.hooks = request.hooks();
            this.checkpointSaver = request.checkpointSaver();
            this.parallelToolExecution = request.agentOptions().parallelToolExecution();
            this.maxParallelTools = request.agentOptions().maxParallelTools();
            this.toolExecutionTimeoutSeconds = request.agentOptions().toolExecutionTimeoutSeconds();
            this.buildCount++;
            return agent;
        }
    }

    private static final class RealAgentBuilder implements DefaultAgentRuntimeFactory.AgentBuilder {

        @Override
        public ReactAgent build(DefaultAgentRuntimeFactory.AgentBuildRequest request) {
            return ReactAgent.builder()
                    .name(request.name())
                    .model(request.model())
                    .chatOptions(request.chatOptions())
                    .systemPrompt(request.systemPrompt())
                    .tools(request.tools())
                    .interceptors(request.interceptors())
                    .hooks(request.hooks())
                    .saver(request.checkpointSaver())
                    .parallelToolExecution(request.agentOptions().parallelToolExecution())
                    .maxParallelTools(request.agentOptions().maxParallelTools())
                    .toolExecutionTimeout(Duration.ofSeconds(request.agentOptions().toolExecutionTimeoutSeconds()))
                    .build();
        }
    }

    private static final class ToolDispatchChatModel implements ChatModel {

        private final String requestedToolName;
        private final List<List<String>> toolSnapshots = new ArrayList<>();
        private final List<String> toolResponses = new ArrayList<>();

        private ToolDispatchChatModel(String requestedToolName) {
            this.requestedToolName = requestedToolName;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            List<String> currentToolNames = prompt.getOptions() instanceof ToolCallingChatOptions options
                    ? options.getToolCallbacks().stream()
                    .map(callback -> callback.getToolDefinition().name())
                    .toList()
                    : List.of();
            toolSnapshots.add(currentToolNames);
            ToolResponseMessage toolResponse = prompt.getInstructions().stream()
                    .filter(ToolResponseMessage.class::isInstance)
                    .map(ToolResponseMessage.class::cast)
                    .reduce((first, second) -> second)
                    .orElse(null);
            if (toolResponse != null) {
                String responseData = toolResponse.getResponses().get(0).responseData();
                toolResponses.add(responseData);
                return response("model observed: " + responseData);
            }
            if (currentToolNames.contains(requestedToolName)) {
                AssistantMessage toolCall = AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall(
                                "tool-call-1", "function", requestedToolName, "{}")))
                        .build();
                return new ChatResponse(List.of(new Generation(toolCall)));
            }
            return response("tool unavailable");
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(call(prompt));
        }

        private ChatResponse response(String content) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
        }

        private List<List<String>> toolSnapshots() {
            return List.copyOf(toolSnapshots);
        }

        private List<String> toolResponses() {
            return List.copyOf(toolResponses);
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
