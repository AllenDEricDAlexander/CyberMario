package top.egon.mario.investment.trading.service.model;

import java.time.Instant;

public record PaperOrderSummary(Long orderId, String status, Instant submittedAt, Instant matchedAt) {
}
