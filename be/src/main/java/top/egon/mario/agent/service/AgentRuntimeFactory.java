package top.egon.mario.agent.service;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import top.egon.mario.agent.observability.service.model.AgentRunAuditContext;
import top.egon.mario.agent.model.service.model.ModelCallContext;
import top.egon.mario.agent.service.model.AgentRuntimeSpec;

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
     * Builds an agent and returns audit metadata derived from the same runtime snapshot.
     */
    default AgentRuntime runtime(AgentRuntimeSpec spec, ModelCallContext context) {
        return new AgentRuntime(get(spec, context), toolDescriptors(spec));
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
