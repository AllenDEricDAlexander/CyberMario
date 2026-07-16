package top.egon.mario.investment.trading.web.dto;

import java.util.List;

public record PaperTradeIntentResponse(
        Long intentId,
        String intentStatus,
        List<PaperRiskCheckResponse> riskResults,
        PaperOrderResponse order,
        PaperFillResponse fill
) {
}
