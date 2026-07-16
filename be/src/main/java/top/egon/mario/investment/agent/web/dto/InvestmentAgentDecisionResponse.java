package top.egon.mario.investment.agent.web.dto;

import top.egon.mario.investment.agent.model.InvestmentAgentAction;
import top.egon.mario.investment.agent.model.InvestmentAgentExecutionStatus;
import top.egon.mario.investment.common.model.OrderType;

import java.time.Instant;
import java.util.List;

public record InvestmentAgentDecisionResponse(
        Long id,
        Long instrumentId,
        InvestmentAgentAction action,
        String confidence,
        String horizon,
        String thesis,
        List<String> risks,
        List<String> invalidation,
        String requestedQuantity,
        String requestedNotional,
        String requestedLeverage,
        OrderType orderType,
        String limitPrice,
        Long intentId,
        InvestmentAgentExecutionStatus executionStatus,
        Instant dataAsOf,
        Instant expiresAt,
        String status,
        Instant createdAt,
        InvestmentAgentExecutionResponse execution
) {
    public InvestmentAgentDecisionResponse {
        risks = List.copyOf(risks);
        invalidation = List.copyOf(invalidation);
    }
}
