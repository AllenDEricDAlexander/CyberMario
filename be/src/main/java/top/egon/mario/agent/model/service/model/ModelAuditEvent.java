package top.egon.mario.agent.model.service.model;

import top.egon.mario.agent.model.dto.enums.ModelProviderType;
import top.egon.mario.agent.model.dto.enums.ModelScenario;
import top.egon.mario.agent.model.dto.request.ModelOptions;
import top.egon.mario.agent.model.po.enums.ModelAuditStatus;

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
