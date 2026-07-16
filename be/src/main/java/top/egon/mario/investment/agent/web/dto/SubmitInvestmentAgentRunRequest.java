package top.egon.mario.investment.agent.web.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import top.egon.mario.investment.agent.model.InvestmentAgentRunType;

import java.util.List;

/** Fixed-preset run request; prompts, tools, and strategy code are intentionally absent. */
public record SubmitInvestmentAgentRunRequest(
        @NotNull InvestmentAgentRunType runType,
        @Positive Long accountId,
        @Size(max = 20) List<@Positive Long> instrumentIds
) {
}
