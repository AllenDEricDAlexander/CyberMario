package top.egon.mario.agent.model.dto.response;

import top.egon.mario.agent.model.dto.enums.ModelProviderType;
import top.egon.mario.agent.model.dto.enums.ModelScenario;
import top.egon.mario.agent.model.po.enums.ModelAuditStatus;

import java.time.Instant;

/**
 * Recent model call row shown below dashboard charts.
 */
public record ModelAuditRecentCallResponse(
        Long id,
        Instant createdAt,
        Long userId,
        String username,
        String nickname,
        ModelProviderType provider,
        String model,
        ModelScenario scenario,
        ModelAuditStatus status,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Long durationMs,
        String traceId
) {
}
