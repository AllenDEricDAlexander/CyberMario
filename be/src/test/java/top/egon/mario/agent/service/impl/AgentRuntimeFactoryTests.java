package top.egon.mario.agent.service.impl;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.interceptor.Interceptor;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
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
import top.egon.mario.agent.observability.interceptor.AgentObservabilityModelInterceptor;
import top.egon.mario.agent.observability.interceptor.AgentObservabilityToolInterceptor;
import top.egon.mario.agent.observability.po.enums.AgentRunToolType;
import top.egon.mario.agent.service.AgentRuntimeFactory;
import top.egon.mario.agent.service.model.AgentModelConfig;
import top.egon.mario.agent.service.model.AgentOptions;
import top.egon.mario.agent.service.model.AgentRuntimeSpec;
import top.egon.mario.agent.service.model.AgentToolConfig;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
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

        assertThat(first).isSameAs(second);
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

    private static final class StubMarioModelFactory implements MarioModelFactory {

        private final ChatModel chatModel = new StubChatModel();
        private ModelRequest request;
        private int resolveCount;

        @Override
        public ModelResolveResult resolve(ModelRequest request) {
            this.request = request;
            this.resolveCount++;
            return new ModelResolveResult(chatModel, request.provider(), request.model(), request.options(),
                    request.context(), ChatOptions.builder().model(request.model()).build());
        }
    }

    private static final class StubAgentBuilder implements DefaultAgentRuntimeFactory.AgentBuilder {

        private final ReactAgent agent = mock(ReactAgent.class);
        private String name;
        private ChatModel model;
        private ChatOptions chatOptions;
        private String systemPrompt;
        private List<ToolCallback> tools = List.of();
        private List<Interceptor> interceptors = List.of();
        private boolean parallelToolExecution;
        private int maxParallelTools;
        private int toolExecutionTimeoutSeconds;
        private int buildCount;

        @Override
        public ReactAgent build(DefaultAgentRuntimeFactory.AgentBuildRequest request) {
            this.name = request.name();
            this.model = request.model();
            this.chatOptions = request.chatOptions();
            this.systemPrompt = request.systemPrompt();
            this.tools = request.tools();
            this.interceptors = request.interceptors();
            this.parallelToolExecution = request.agentOptions().parallelToolExecution();
            this.maxParallelTools = request.agentOptions().maxParallelTools();
            this.toolExecutionTimeoutSeconds = request.agentOptions().toolExecutionTimeoutSeconds();
            this.buildCount++;
            return agent;
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
