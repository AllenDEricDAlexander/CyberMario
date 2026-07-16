package top.egon.mario.investment.agent.web.dto;

import top.egon.mario.investment.agent.model.InvestmentAgentRunType;
import top.egon.mario.investment.common.model.InvestmentRunStatus;

import java.time.Instant;

public record InvestmentAgentRunResponse(
        Long id,
        Long workspaceId,
        Long accountId,
        String presetCode,
        Long genericAgentRunAuditId,
        InvestmentAgentRunType runType,
        InvestmentRunStatus status,
        Instant dataAsOf,
        Long reportId,
        Instant startedAt,
        Instant finishedAt,
        String errorCode,
        String errorMessage,
        Instant createdAt
) {
}
