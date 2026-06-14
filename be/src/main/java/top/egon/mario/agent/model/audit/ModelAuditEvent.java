package top.egon.mario.agent.model.audit;

import top.egon.mario.agent.model.api.ModelOptions;
import top.egon.mario.agent.model.api.ModelProviderType;
import top.egon.mario.agent.model.api.ModelScenario;

import java.time.Instant;

/**
 * Immutable model call audit event produced after a chat model invocation finishes.
 */
public record ModelAuditEvent(
        String requestId,
        String traceId,
        Long userId,
        String sessionId,
        String threadId,
        ModelScenario scenario,
        ModelProviderType provider,
        String model,
        ModelOptions options,
        ModelTokenUsage tokenUsage,
        boolean streaming,
        ModelAuditStatus status,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        String errorCode,
        String errorMessage,
        Integer promptChars,
        Integer completionChars,
        String ip,
        String userAgent
) {
}
