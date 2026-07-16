package top.egon.mario.investment.agent.web.dto;

public record SubmitInvestmentAgentRunResponse(
        InvestmentAgentRunResponse run,
        Long jobId,
        boolean duplicate
) {
}
