package top.egon.mario.investment.portfolio.web.dto;

import java.time.Instant;

public record InvestmentPositionResponse(
        Long id,
        Long instrumentId,
        String positionSide,
        String quantity,
        String entryPrice,
        String leverage,
        String markPrice,
        String liquidationPrice,
        String isolatedMargin,
        String maintenanceMargin,
        String realizedPnl,
        String fundingPnl,
        String unrealizedPnl,
        Instant lastFillAt,
        Instant lastMarginCheckAt
) {
}
