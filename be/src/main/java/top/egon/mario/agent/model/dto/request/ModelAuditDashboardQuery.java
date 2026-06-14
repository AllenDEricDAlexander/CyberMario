package top.egon.mario.agent.model.dto.request;

import top.egon.mario.agent.model.dto.enums.ModelProviderType;
import top.egon.mario.agent.model.dto.enums.ModelScenario;
import top.egon.mario.agent.model.po.enums.ModelAuditStatus;

import java.time.Instant;

/**
 * Filter criteria accepted by the model audit dashboard.
 */
public record ModelAuditDashboardQuery(
        Instant startAt,
        Instant endAt,
        Long userId,
        ModelProviderType provider,
        String model,
        ModelScenario scenario,
        ModelAuditStatus status
) {
}
