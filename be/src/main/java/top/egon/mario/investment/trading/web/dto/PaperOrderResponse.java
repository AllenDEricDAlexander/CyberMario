package top.egon.mario.investment.trading.web.dto;

import java.time.Instant;

public record PaperOrderResponse(Long orderId, String status, Instant submittedAt, Instant matchedAt) {
}
