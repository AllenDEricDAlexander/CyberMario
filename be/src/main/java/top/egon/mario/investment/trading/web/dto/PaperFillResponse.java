package top.egon.mario.investment.trading.web.dto;

import java.time.Instant;

public record PaperFillResponse(
        Long fillId, String fillPrice, String quantity, String feeAmount, Instant filledAt) {
}
