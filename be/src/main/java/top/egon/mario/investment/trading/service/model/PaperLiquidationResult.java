package top.egon.mario.investment.trading.service.model;

public record PaperLiquidationResult(
        long accountId,
        long positionId,
        Long orderId,
        String status,
        boolean idempotent
) {
}
