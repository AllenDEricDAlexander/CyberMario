package top.egon.mario.investment.trading.service.model;

import java.math.BigDecimal;

public record PaperFundingResult(
        long accountId,
        long positionId,
        String status,
        BigDecimal amount,
        boolean idempotent
) {
}
