package top.egon.mario.agent.service;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import top.egon.mario.agent.model.service.model.ModelCallContext;
import top.egon.mario.agent.service.model.AgentRuntimeSpec;

/**
 * Builds or reuses ReactAgent instances for resolved debug runtime specs.
 */
public interface AgentRuntimeFactory {

    /**
     * Returns an isolated agent for the supplied runtime spec.
     */
    ReactAgent get(AgentRuntimeSpec spec, ModelCallContext context);

}
