package top.egon.mario.investment.quant.strategy;

import top.egon.mario.investment.common.model.PositionSide;
import top.egon.mario.investment.trading.matching.model.FuturesBar;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Explicit as-of context supplied to a code strategy without repositories or clocks.
 */
public record StrategyContext(
        long instrumentId,
        Instant evaluationTime,
        Instant dataAsOf,
        List<FuturesBar> bars,
        PositionSide currentPositionSide
) {

    public StrategyContext {
        if (instrumentId <= 0) {
            throw new IllegalArgumentException("instrumentId must be positive");
        }
        Objects.requireNonNull(evaluationTime, "evaluationTime");
        Objects.requireNonNull(dataAsOf, "dataAsOf");
        if (evaluationTime.isAfter(dataAsOf)) {
            throw new IllegalArgumentException("evaluationTime must not exceed dataAsOf");
        }
        bars = List.copyOf(Objects.requireNonNull(bars, "bars"));
        if (bars.isEmpty() || bars.stream().anyMatch(bar -> !bar.closed()
                || bar.closeTime().isAfter(evaluationTime) || bar.closeTime().isAfter(dataAsOf))) {
            throw new IllegalArgumentException("strategies require closed bars within the evaluation cutoff");
        }
    }
}
