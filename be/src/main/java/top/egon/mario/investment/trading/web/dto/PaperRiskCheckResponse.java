package top.egon.mario.investment.trading.web.dto;

import java.time.Instant;
import java.util.Map;

public record PaperRiskCheckResponse(
        String ruleCode,
        boolean passed,
        String observedValue,
        String limitValue,
        String message,
        Map<String, String> details,
        Instant checkedAt
) {
}
