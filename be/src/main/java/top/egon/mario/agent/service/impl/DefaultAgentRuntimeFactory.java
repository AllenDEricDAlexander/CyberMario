package top.egon.mario.agent.service.impl;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.egon.mario.agent.hooks.LoggingHook;
import top.egon.mario.agent.interceptor.ToolMonitorInterceptor;
import top.egon.mario.agent.mcp.runtime.McpAgentToolProvider;
import top.egon.mario.agent.model.dto.request.ModelRequest;
import top.egon.mario.agent.model.dto.response.ModelResolveResult;
import top.egon.mario.agent.model.service.MarioModelFactory;
import top.egon.mario.agent.model.service.model.ModelCallContext;
import top.egon.mario.agent.service.AgentRuntimeFactory;
import top.egon.mario.agent.service.model.AgentOptions;
import top.egon.mario.agent.service.model.AgentRuntimeSpec;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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

    public DefaultAgentRuntimeFactory(MarioModelFactory marioModelFactory, List<ToolCallback> toolCallbacks) {
        this(marioModelFactory, toolCallbacks, new ReactAgentBuilder(), null);
    }

    @Autowired
    public DefaultAgentRuntimeFactory(MarioModelFactory marioModelFactory, List<ToolCallback> toolCallbacks,
                                      ObjectProvider<McpAgentToolProvider> mcpAgentToolProvider) {
        this(marioModelFactory, toolCallbacks, new ReactAgentBuilder(),
                mcpAgentToolProvider == null ? null : mcpAgentToolProvider.getIfAvailable());
    }

    DefaultAgentRuntimeFactory(MarioModelFactory marioModelFactory, List<ToolCallback> toolCallbacks, AgentBuilder agentBuilder) {
        this(marioModelFactory, toolCallbacks, agentBuilder, null);
    }

    DefaultAgentRuntimeFactory(MarioModelFactory marioModelFactory, List<ToolCallback> toolCallbacks,
                               AgentBuilder agentBuilder, McpAgentToolProvider mcpAgentToolProvider) {
        this.marioModelFactory = marioModelFactory;
        this.toolCallbacks = toolCallbacks == null ? List.of() : List.copyOf(toolCallbacks);
        this.agentBuilder = agentBuilder;
        this.mcpAgentToolProvider = mcpAgentToolProvider;
    }

    @Override
    public ReactAgent get(AgentRuntimeSpec spec, ModelCallContext context) {
        return buildAgent(spec, context);
    }

    private ReactAgent buildAgent(AgentRuntimeSpec spec, ModelCallContext context) {
        ModelResolveResult model = marioModelFactory.resolve(new ModelRequest(
                spec.modelConfig().provider(),
                spec.modelConfig().model(),
                spec.modelOptions(),
                context
        ));
        return agentBuilder.build(new AgentBuildRequest(
                AGENT_NAME,
                model.chatModel(),
                model.chatOptions(),
                spec.systemPrompt(),
                enabledTools(spec),
                normalizeAgentOptions(spec.agentOptions())
        ));
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
                    .interceptors(new ToolMonitorInterceptor())
                    .hooks(new LoggingHook())
                    .saver(new MemorySaver())
                    .parallelToolExecution(request.agentOptions().parallelToolExecution())
                    .maxParallelTools(request.agentOptions().maxParallelTools())
                    .toolExecutionTimeout(Duration.ofSeconds(request.agentOptions().toolExecutionTimeoutSeconds()))
                    .build();
        }
    }

}
