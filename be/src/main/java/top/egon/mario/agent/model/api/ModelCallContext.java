package top.egon.mario.agent.model.api;

/**
 * Audit context for one resolved model call path.
 */
public record ModelCallContext(
        Long userId,
        String traceId,
        String sessionId,
        String threadId,
        ModelScenario scenario,
        String requestId,
        String ip,
        String userAgent
) {

    public ModelCallContext {
        scenario = scenario == null ? ModelScenario.UNKNOWN : scenario;
    }

}
