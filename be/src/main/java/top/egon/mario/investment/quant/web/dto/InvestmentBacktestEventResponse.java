package top.egon.mario.investment.quant.web.dto;

import java.time.Instant;

public record InvestmentBacktestEventResponse(Long eventId, Long instrumentId, String eventType,
                                              Instant eventTime, String amount, String balanceAfter,
                                              String detailsJson, Long sequenceNo) {
}
