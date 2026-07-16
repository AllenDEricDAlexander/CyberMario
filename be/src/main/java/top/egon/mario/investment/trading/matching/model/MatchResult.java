package top.egon.mario.investment.trading.matching.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Deterministic whole-order result for one closed bar.
 */
public record MatchResult(
        long orderId,
        MatchStatus status,
        Instant marketBarOpenTime,
        TradeSide tradeSide,
        LiquidityRole liquidityRole,
        BigDecimal fillPrice,
        BigDecimal quantity,
        BigDecimal notional,
        BigDecimal fee
) {

    public static MatchResult waiting(MatchingOrder order) {
        return new MatchResult(order.orderId(), MatchStatus.WAITING, null, order.tradeSide(),
                null, null, null, null, null);
    }

    public static MatchResult filled(MatchingOrder order, Instant marketBarOpenTime, TradeSide tradeSide,
                                     LiquidityRole liquidityRole, BigDecimal fillPrice, BigDecimal quantity,
                                     BigDecimal notional, BigDecimal fee) {
        return new MatchResult(order.orderId(), MatchStatus.FILLED, marketBarOpenTime, tradeSide,
                liquidityRole, fillPrice, quantity, notional, fee);
    }
}
