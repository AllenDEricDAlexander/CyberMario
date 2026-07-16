package top.egon.mario.investment.portfolio.web.dto;

import java.time.Instant;

public record InvestmentLedgerResponse(
        Long id,
        Long sequenceNo,
        String eventType,
        String amount,
        String balanceAfter,
        Long instrumentId,
        String referenceType,
        String referenceId,
        Instant occurredAt
) {
}
