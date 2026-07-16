package top.egon.mario.investment.trading.matching.model;

import top.egon.mario.investment.common.model.OrderType;
import top.egon.mario.investment.common.model.PositionAction;
import top.egon.mario.investment.common.model.PositionSide;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable order facts accepted by the closed-bar matching model.
 */
public record MatchingOrder(
        long orderId,
        OrderType orderType,
        PositionSide positionSide,
        PositionAction positionAction,
        BigDecimal quantity,
        BigDecimal limitPrice,
        Instant eligibleAfter
) {

    public MatchingOrder {
        if (orderId <= 0) {
            throw new IllegalArgumentException("orderId must be positive");
        }
        Objects.requireNonNull(orderType, "orderType");
        Objects.requireNonNull(positionSide, "positionSide");
        Objects.requireNonNull(positionAction, "positionAction");
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (orderType == OrderType.LIMIT && (limitPrice == null || limitPrice.signum() <= 0)) {
            throw new IllegalArgumentException("limitPrice is required for a limit order");
        }
        if (orderType == OrderType.MARKET && limitPrice != null) {
            throw new IllegalArgumentException("limitPrice is not allowed for a market order");
        }
        Objects.requireNonNull(eligibleAfter, "eligibleAfter");
    }

    public TradeSide tradeSide() {
        boolean opening = positionAction == PositionAction.OPEN;
        if (positionSide == PositionSide.LONG) {
            return opening ? TradeSide.BUY : TradeSide.SELL;
        }
        return opening ? TradeSide.SELL : TradeSide.BUY;
    }
}
