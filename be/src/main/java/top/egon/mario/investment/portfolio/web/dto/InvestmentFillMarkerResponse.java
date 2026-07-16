package top.egon.mario.investment.portfolio.web.dto;

import java.time.Instant;

public record InvestmentFillMarkerResponse(
        Long id,
        Long instrumentId,
        Instant marketBarOpenTime,
        Instant eventTime,
        String side,
        String actionType,
        String orderOrigin,
        String eventType,
        String price,
        String quantity,
        boolean liquidation
) {
}
