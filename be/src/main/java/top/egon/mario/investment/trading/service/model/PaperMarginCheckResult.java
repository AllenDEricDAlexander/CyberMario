package top.egon.mario.investment.trading.service.model;

import java.math.BigDecimal;

public record PaperMarginCheckResult(
        long accountId,
        long positionId,
        String status,
        BigDecimal positionEquity,
        BigDecimal liquidationThreshold,
        Long liquidationOrderId
) {
}
