package top.egon.mario.investment.trading.service.model;

import java.math.BigDecimal;

public record PositionMarkSnapshot(
        long instrumentId,
        BigDecimal markPrice,
        BigDecimal contractMultiplier
) {
}
