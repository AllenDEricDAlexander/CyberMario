package top.egon.mario.investment.agent.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record InvestmentAgentExecutionResponse(
        Long intentId,
        String intentStatus,
        List<RiskCheck> riskChecks,
        Order order,
        Fill fill
) {
    public InvestmentAgentExecutionResponse {
        riskChecks = riskChecks == null ? List.of() : List.copyOf(riskChecks);
    }

    public record RiskCheck(
            String ruleCode,
            boolean passed,
            String observedValue,
            String limitValue,
            String message,
            Map<String, String> details,
            Instant checkedAt
    ) {
        public RiskCheck {
            details = details == null ? Map.of() : Map.copyOf(details);
        }
    }

    public record Order(Long id, String status, Instant submittedAt, Instant matchedAt) {
    }

    public record Fill(Long id, String price, String quantity, String feeAmount, Instant filledAt) {
    }
}
