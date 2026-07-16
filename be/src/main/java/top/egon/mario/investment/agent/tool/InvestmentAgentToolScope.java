package top.egon.mario.investment.agent.tool;

import java.time.Instant;
import java.util.List;

/** Security and cutoff boundary captured by every per-run read callback closure. */
public record InvestmentAgentToolScope(
        long actorId,
        long workspaceId,
        Long accountId,
        List<Long> instrumentIds,
        Instant dataAsOf
) {
    public InvestmentAgentToolScope {
        instrumentIds = List.copyOf(instrumentIds);
    }
}
