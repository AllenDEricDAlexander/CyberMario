package top.egon.mario.agent.service.model;

import java.util.Set;

/**
 * Tool allow-list used when building a debug agent runtime.
 */
public record AgentToolConfig(
        Set<String> enabledToolNames
) {

    public AgentToolConfig {
        enabledToolNames = enabledToolNames == null ? null : Set.copyOf(enabledToolNames);
    }

}
