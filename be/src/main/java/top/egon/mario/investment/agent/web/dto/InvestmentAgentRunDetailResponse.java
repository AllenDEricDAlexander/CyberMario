package top.egon.mario.investment.agent.web.dto;

import java.util.List;

public record InvestmentAgentRunDetailResponse(
        InvestmentAgentRunResponse run,
        List<InvestmentAgentDecisionResponse> decisions
) {
    public InvestmentAgentRunDetailResponse {
        decisions = List.copyOf(decisions);
    }
}
