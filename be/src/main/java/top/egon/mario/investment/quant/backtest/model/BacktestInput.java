package top.egon.mario.investment.quant.backtest.model;

import top.egon.mario.investment.quant.strategy.InvestmentStrategy;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record BacktestInput(InvestmentStrategy strategy, BigDecimal initialEquity,
                            List<BacktestInstrumentInput> instruments) {

    public BacktestInput {
        Objects.requireNonNull(strategy, "strategy");
        if (initialEquity == null || initialEquity.signum() <= 0) {
            throw new IllegalArgumentException("initialEquity must be positive");
        }
        instruments = instruments == null ? List.of() : instruments.stream()
                .sorted(Comparator.comparingLong(BacktestInstrumentInput::instrumentId)).toList();
        if (instruments.isEmpty()) {
            throw new IllegalArgumentException("At least one instrument is required");
        }
    }
}
