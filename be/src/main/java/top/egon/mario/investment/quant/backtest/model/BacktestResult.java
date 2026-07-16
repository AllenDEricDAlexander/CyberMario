package top.egon.mario.investment.quant.backtest.model;

import java.util.List;

public record BacktestResult(BacktestMetrics metrics, List<BacktestTrade> trades,
                             List<BacktestEvent> events, List<BacktestEquityPoint> equityPoints) {
    public BacktestResult {
        trades = List.copyOf(trades);
        events = List.copyOf(events);
        equityPoints = List.copyOf(equityPoints);
    }
}
