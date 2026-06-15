package top.egon.mario.agent.service.model;

/**
 * ReactAgent construction options exposed by the debug workspace.
 */
public record AgentOptions(
        Boolean parallelToolExecution,
        Integer maxParallelTools,
        Integer toolExecutionTimeoutSeconds
) {
}
