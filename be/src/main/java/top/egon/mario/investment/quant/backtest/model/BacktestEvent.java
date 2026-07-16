package top.egon.mario.investment.quant.backtest.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record BacktestEvent(long sequenceNo, Long instrumentId, String eventType,
                            Instant eventTime, BigDecimal amount,
                            BigDecimal balanceAfter, Map<String, Object> details) {

    public BacktestEvent {
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
