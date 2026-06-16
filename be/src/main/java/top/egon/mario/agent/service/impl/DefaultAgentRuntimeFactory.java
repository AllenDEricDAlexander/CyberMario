package top.egon.mario.agent.service.impl;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.alibaba.cloud.ai.graph.agent.interceptor.Interceptor;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.egon.mario.agent.hooks.LoggingHook;
import top.egon.mario.agent.interceptor.ToolMonitorInterceptor;
import top.egon.mario.agent.memory.checkpoint.AgentMemoryCheckpointerProvider;
import top.egon.mario.agent.mcp.runtime.McpAgentToolProvider;
import top.egon.mario.agent.mcp.runtime.LoggingMcpToolCallback;
import top.egon.mario.agent.model.dto.request.ModelRequest;
import top.egon.mario.agent.model.dto.response.ModelResolveResult;
import top.egon.mario.agent.model.service.MarioModelFactory;
import top.egon.mario.agent.model.service.model.ModelCallContext;
import top.egon.mario.agent.observability.po.enums.AgentRunToolType;
import top.egon.mario.agent.observability.service.model.AgentRunAuditContext;
import top.egon.mario.agent.service.AgentRuntimeFactory;
import top.egon.mario.agent.service.model.AgentOptions;
import top.egon.mario.agent.service.model.AgentRuntimeSpec;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default runtime factory that resolves audited models and builds ReactAgent instances for each request context.
 */
@Service
public class DefaultAgentRuntimeFactory implements AgentRuntimeFactory {

    private static final String AGENT_NAME = "cyber_mario_agent";

    private final MarioModelFactory marioModelFactory;
    private final List<ToolCallback> toolCallbacks;
    private final AgentBuilder agentBuilder;
    private final McpAgentToolProvider mcpAgentToolProvider;
    private final List<Interceptor> interceptors;
    private final AgentMemoryCheckpointerProvider checkpointerProvider;
    private final List<Hook> hooks;

    public DefaultAgentRuntimeFactory(MarioModelFactory marioModelFactory, List<ToolCallback> toolCallbacks) {
        this(marioModelFactory, toolCallbacks, new ReactAgentBuilder(), null, List.of(), null, List.of());
    }

    @Autowired
    public DefaultAgentRuntimeFactory(MarioModelFactory marioModelFactory, List<ToolCallback> toolCallbacks,
                                      ObjectProvider<McpAgentToolProvider> mcpAgentToolProvider,
                                      ObjectProvider<Interceptor> interceptors,
                                      ObjectProvider<AgentMemoryCheckpointerProvider> checkpointerProvider,
                                      ObjectProvider<Hook> hooks) {
        this(marioModelFactory, toolCallbacks, new ReactAgentBuilder(),
                mcpAgentToolProvider == null ? null : mcpAgentToolProvider.getIfAvailable(),
                interceptors == null ? List.of() : interceptors.orderedStream().toList(),
                checkpointerProvider == null ? null : checkpointerProvider.getIfAvailable(),
                hooks == null ? List.of() : hooks.orderedStream().toList());
    }

    DefaultAgentRuntimeFactory(MarioModelFactory marioModelFactory, List<ToolCallback> toolCallbacks, AgentBuilder agentBuilder) {
        this(marioModelFactory, toolCallbacks, agentBuilder, null, List.of(), null, List.of());
    }

    DefaultAgentRuntimeFactory(MarioModelFactory marioModelFactory, List<ToolCallback> toolCallbacks,
                               AgentBuilder agentBuilder, McpAgentToolProvider mcpAgentToolProvider) {
        this(marioModelFactory, toolCallbacks, agentBuilder, mcpAgentToolProvider, List.of(), null, List.of());
    }

    DefaultAgentRuntimeFactory(MarioModelFactory marioModelFactory, List<ToolCallback> toolCallbacks,
                               AgentBuilder agentBuilder, McpAgentToolProvider mcpAgentToolProvider,
                               List<Interceptor> interceptors) {
        this(marioModelFactory, toolCallbacks, agentBuilder, mcpAgentToolProvider, interceptors, null, List.of());
    }

    DefaultAgentRuntimeFactory(MarioModelFactory marioModelFactory, List<ToolCallback> toolCallbacks,
                               AgentBuilder agentBuilder, McpAgentToolProvider mcpAgentToolProvider,
                               List<Interceptor> interceptors,
                               AgentMemoryCheckpointerProvider checkpointerProvider,
                               List<Hook> hooks) {
        this.marioModelFactory = marioModelFactory;
        this.toolCallbacks = toolCallbacks == null ? List.of() : List.copyOf(toolCallbacks);
        this.agentBuilder = agentBuilder;
        this.mcpAgentToolProvider = mcpAgentToolProvider;
        this.interceptors = interceptors == null ? List.of() : List.copyOf(interceptors);
        this.checkpointerProvider = checkpointerProvider;
        this.hooks = hooks == null ? List.of() : List.copyOf(hooks);
    }

    @Override
    public ReactAgent get(AgentRuntimeSpec spec, ModelCallContext context) {
        return runtime(spec, context).agent();
    }

    @Override
    public AgentRuntime runtime(AgentRuntimeSpec spec, ModelCallContext context) {
        List<ToolCallback> enabledTools = enabledTools(spec);
        ModelResolveResult model = marioModelFactory.resolve(new ModelRequest(
                spec.modelConfig().provider(),
                spec.modelConfig().model(),
                spec.modelOptions(),
                context
        ));
        ReactAgent agent = agentBuilder.build(new AgentBuildRequest(
                AGENT_NAME,
                model.chatModel(),
                model.chatOptions(),
                spec.systemPrompt(),
                enabledTools,
                agentInterceptors(),
                agentHooks(),
                checkpointSaver(),
                normalizeAgentOptions(spec.agentOptions())
        ));
        return new AgentRuntime(agent, toolDescriptors(enabledTools));
    }

    @Override
    public Map<String, AgentRunAuditContext.ToolDescriptor> toolDescriptors(AgentRuntimeSpec spec) {
        return toolDescriptors(enabledTools(spec));
    }

    private Map<String, AgentRunAuditContext.ToolDescriptor> toolDescriptors(List<ToolCallback> callbacks) {
        Map<String, AgentRunAuditContext.ToolDescriptor> descriptors = new LinkedHashMap<>();
        for (ToolCallback callback : callbacks) {
            String toolName = callback.getToolDefinition().name();
            if (callback instanceof LoggingMcpToolCallback mcpTool) {
                descriptors.put(toolName, new AgentRunAuditContext.ToolDescriptor(AgentRunToolType.MCP,
                        mcpTool.serverCode()));
            } else {
                descriptors.put(toolName, new AgentRunAuditContext.ToolDescriptor(AgentRunToolType.LOCAL, null));
            }
        }
        return descriptors;
    }

    private List<Interceptor> agentInterceptors() {
        List<Interceptor> values = new ArrayList<>();
        values.addAll(interceptors);
        values.add(new ToolMonitorInterceptor());
        return values;
    }

    private List<Hook> agentHooks() {
        List<Hook> values = new ArrayList<>(hooks);
        values.add(new LoggingHook());
        return values;
    }

    private BaseCheckpointSaver checkpointSaver() {
        return checkpointerProvider == null ? new MemorySaver() : checkpointerProvider.saver();
    }

    private List<ToolCallback> enabledTools(AgentRuntimeSpec spec) {
        if (spec.toolConfig() == null || spec.toolConfig().enabledToolNames() == null
                || spec.toolConfig().enabledToolNames().isEmpty()) {
            return List.of();
        }
        return currentToolCallbacks().stream()
                .filter(toolCallback -> spec.toolConfig().enabledToolNames()
                        .contains(toolCallback.getToolDefinition().name()))
                .toList();
    }

    private List<ToolCallback> currentToolCallbacks() {
        List<ToolCallback> callbacks = new ArrayList<>(toolCallbacks);
        Set<String> toolNames = toolCallbacks.stream()
                .map(toolCallback -> toolCallback.getToolDefinition().name())
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
        if (mcpAgentToolProvider == null) {
            return callbacks;
        }
        ToolCallback[] mcpToolCallbacks = mcpAgentToolProvider.currentToolCallbacks();
        if (mcpToolCallbacks == null || mcpToolCallbacks.length == 0) {
            return callbacks;
        }
        for (ToolCallback toolCallback : mcpToolCallbacks) {
            if (toolNames.add(toolCallback.getToolDefinition().name())) {
                callbacks.add(toolCallback);
            }
        }
        return callbacks;
    }

    private AgentOptions normalizeAgentOptions(AgentOptions options) {
        if (options == null) {
            return new AgentOptions(false, 5, 300);
        }
        return new AgentOptions(
                options.parallelToolExecution() != null && options.parallelToolExecution(),
                options.maxParallelTools() == null ? 5 : options.maxParallelTools(),
                options.toolExecutionTimeoutSeconds() == null ? 300 : options.toolExecutionTimeoutSeconds()
        );
    }

    /**
     * Adapter for building ReactAgent instances, separated to keep runtime construction testable.
     */
    interface AgentBuilder {

        ReactAgent build(AgentBuildRequest request);

    }

    /**
     * Parameters passed to the low-level ReactAgent builder.
     */
    record AgentBuildRequest(
            String name,
            ChatModel model,
            ChatOptions chatOptions,
            String systemPrompt,
            List<ToolCallback> tools,
            List<Interceptor> interceptors,
            List<Hook> hooks,
            BaseCheckpointSaver checkpointSaver,
            AgentOptions agentOptions
    ) {
    }

    private static final class ReactAgentBuilder implements AgentBuilder {

        @Override
        public ReactAgent build(AgentBuildRequest request) {
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

}
