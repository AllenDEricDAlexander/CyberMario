package top.egon.mario.investment.research.web.dto;

import java.time.Instant;

/**
 * Private Investment workspace summary.
 */
public record InvestmentWorkspaceResponse(
        Long id,
        String name,
        String baseCurrency,
        String timezone,
        String status,
        Instant createdAt
) {
}
