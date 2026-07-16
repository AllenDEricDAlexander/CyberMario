package top.egon.mario.investment.trading.service.model;

public record PaperExecutionResult(Long orderId, String orderStatus, PaperFillSummary fill, boolean idempotent) {
}
