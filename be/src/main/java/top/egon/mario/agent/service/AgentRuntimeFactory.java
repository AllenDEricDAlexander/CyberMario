package top.egon.mario.agent.service;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import top.egon.mario.agent.observability.service.model.AgentRunAuditContext;
import top.egon.mario.agent.model.service.model.ModelCallContext;
import top.egon.mario.agent.service.model.AgentRuntimeSpec;
import top.egon.mario.agent.service.model.ScopedAgentToolSet;

import java.util.Map;

/**
 * Builds or reuses ReactAgent instances for resolved debug runtime specs.
 */
public interface AgentRuntimeFactory {

    /**
     * Returns an isolated agent for the supplied runtime spec.
     */
    ReactAgent get(AgentRuntimeSpec spec, ModelCallContext context);

    /**
     * Returns an isolated agent with callbacks scoped to this runtime creation only.
     */
    default ReactAgent get(AgentRuntimeSpec spec, ModelCallContext context, ScopedAgentToolSet scopedTools) {
        return runtime(spec, context, scopedTools).agent();
    }

    /**
     * Builds an agent and returns audit metadata derived from the same runtime snapshot.
     */
    default AgentRuntime runtime(AgentRuntimeSpec spec, ModelCallContext context) {
        return new AgentRuntime(get(spec, context), toolDescriptors(spec));
    }

    /**
     * Builds a runtime with callbacks that are never added to the factory's default tool registry.
     */
    default AgentRuntime runtime(AgentRuntimeSpec spec, ModelCallContext context, ScopedAgentToolSet scopedTools) {
        ScopedAgentToolSet tools = scopedTools == null ? ScopedAgentToolSet.empty() : scopedTools;
        if (!tools.isEmpty()) {
            throw new UnsupportedOperationException("scoped agent tools are not supported by this runtime factory");
        }
        return runtime(spec, context);
    }

    /**
     * Returns descriptors for the tool callbacks enabled by the supplied runtime spec.
     */
    default Map<String, AgentRunAuditContext.ToolDescriptor> toolDescriptors(AgentRuntimeSpec spec) {
        return Map.of();
    }

    record AgentRuntime(ReactAgent agent, Map<String, AgentRunAuditContext.ToolDescriptor> toolDescriptors) {
    }

}
