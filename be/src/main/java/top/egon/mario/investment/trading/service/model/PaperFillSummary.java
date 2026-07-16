package top.egon.mario.investment.trading.service.model;

import java.math.BigDecimal;
import java.time.Instant;

public record PaperFillSummary(
        Long fillId, BigDecimal fillPrice, BigDecimal quantity, BigDecimal feeAmount, Instant filledAt) {
}
