package top.egon.mario.investment.agent.model;

import top.egon.mario.investment.common.model.OrderType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Strict final model output after JSON shape and server-bound scope validation. */
public record InvestmentAgentDecisionProposal(
        Long instrumentId,
        InvestmentAgentAction action,
        BigDecimal confidence,
        String horizon,
        String thesis,
        List<String> risks,
        List<String> invalidation,
        BigDecimal requestedQuantity,
        BigDecimal requestedNotional,
        BigDecimal requestedLeverage,
        OrderType orderType,
        BigDecimal limitPrice,
        Instant dataAsOf,
        Instant expiresAt
) {
    public InvestmentAgentDecisionProposal {
        risks = risks == null ? null : List.copyOf(risks);
        invalidation = invalidation == null ? null : List.copyOf(invalidation);
    }
}
