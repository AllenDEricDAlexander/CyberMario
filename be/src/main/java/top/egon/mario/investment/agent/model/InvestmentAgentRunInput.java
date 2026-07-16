package top.egon.mario.investment.agent.model;

import java.time.Instant;
import java.util.List;

/** Canonical, server-bound input persisted before the asynchronous Agent run starts. */
public record InvestmentAgentRunInput(
        long actorId,
        long workspaceId,
        Long accountId,
        InvestmentAgentRunType runType,
        List<Long> instrumentIds,
        Instant dataAsOf
) {
    public InvestmentAgentRunInput {
        instrumentIds = instrumentIds == null ? List.of() : List.copyOf(instrumentIds);
    }
}
