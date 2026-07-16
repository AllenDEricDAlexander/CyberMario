package top.egon.mario.investment.quant;

import org.junit.jupiter.api.Test;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.portfolio.margin.PositionTier;
import top.egon.mario.investment.quant.backtest.model.BacktestInput;
import top.egon.mario.investment.quant.backtest.BacktestEquityPointSelector;
import top.egon.mario.investment.quant.backtest.model.BacktestEquityPoint;
import top.egon.mario.investment.quant.backtest.model.BacktestInstrumentInput;
import top.egon.mario.investment.quant.backtest.model.FundingPoint;
import top.egon.mario.investment.quant.engine.JavaBacktestEngine;
import top.egon.mario.investment.quant.strategy.InvestmentStrategy;
import top.egon.mario.investment.quant.strategy.StrategyContext;
import top.egon.mario.investment.quant.strategy.StrategyDecision;
import top.egon.mario.investment.quant.strategy.StrategyDescriptor;
import top.egon.mario.investment.quant.strategy.StrategyEngineType;
import top.egon.mario.investment.quant.strategy.StrategySignal;
import top.egon.mario.investment.trading.matching.model.ContractTerms;
import top.egon.mario.investment.trading.matching.model.FuturesBar;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JavaBacktestEngineTests {

    private static final Instant START = Instant.parse("2035-01-01T00:00:00Z");

    private final JavaBacktestEngine engine = new JavaBacktestEngine();

    @Test
    void evaluatesBarNAndFillsOnlyAtBarNPlusOneDeterministically() {
        BacktestInput input = new BacktestInput(new OpenThenCloseStrategy(), decimal("10000"),
                List.of(instrument(11L, normalMarkBars())));

        var first = engine.run(input);
        var second = engine.run(input);

        assertThat(first).isEqualTo(second);
        assertThat(first.trades()).singleElement().satisfies(trade -> {
            assertThat(trade.entryTime()).isEqualTo(START.plusSeconds(60));
            assertThat(trade.exitTime()).isEqualTo(START.plusSeconds(120));
            assertThat(trade.entryPrice()).isGreaterThan(decimal("101"));
            assertThat(trade.feeAmount()).isPositive();
            assertThat(trade.fundingAmount()).isNegative();
        });
        assertThat(first.events()).extracting(event -> event.eventType())
                .contains("FILL", "FEE", "FUNDING");
        assertThat(first.equityPoints()).hasSize(3);
        assertThat(first.metrics().annualizedReturn()).isNotEqualByComparingTo(BigDecimal.ZERO);
        assertThat(first.metrics().sharpeRatio()).isNotEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void liquidationUsesMarkBarAndPersistsDeterministicPriority() {
        List<FuturesBar> marks = List.of(bar(0, "100", "105", "95", "100"),
                bar(1, "100", "101", "1", "50"), bar(2, "50", "60", "40", "55"));

        var result = engine.run(new BacktestInput(new OpenThenHoldStrategy(), decimal("10000"),
                List.of(instrument(11L, marks))));

        assertThat(result.metrics().liquidationCount()).isEqualTo(1);
        assertThat(result.trades()).singleElement().satisfies(trade ->
                assertThat(trade.exitReason()).isEqualTo("LIQUIDATION"));
        assertThat(result.events()).extracting(event -> event.eventType()).contains("LIQUIDATION");
    }

    @Test
    void sameTimestampEventsAreOrderedByInstrumentThenEventPriority() {
        var result = engine.run(new BacktestInput(new OpenThenCloseStrategy(), decimal("10000"),
                List.of(instrument(22L, normalMarkBars()), instrument(11L, normalMarkBars()))));

        assertThat(result.events()).isSortedAccordingTo(java.util.Comparator
                .comparing((top.egon.mario.investment.quant.backtest.model.BacktestEvent event) -> event.eventTime())
                .thenComparing(event -> event.instrumentId(), java.util.Comparator.nullsLast(Long::compareTo))
                .thenComparingLong(event -> event.sequenceNo()));
        assertThat(result.events().stream().filter(event -> event.eventTime().equals(START.plusSeconds(60)))
                .map(event -> event.instrumentId()).distinct().toList()).containsExactly(11L, 22L);
    }

    @Test
    void equityPersistenceSelectionKeepsBoundariesEventsAndBoundedSamples() {
        List<BacktestEquityPoint> points = java.util.stream.IntStream.range(0, 6_100)
                .mapToObj(index -> new BacktestEquityPoint(START.plusSeconds(index), decimal("10000"),
                        BigDecimal.ZERO, BigDecimal.ZERO, decimal("10000"), BigDecimal.ZERO,
                        BigDecimal.ZERO, index == 3_050)).collect(java.util.stream.Collectors.toList());
        points.set(3_051, new BacktestEquityPoint(START.plusSeconds(3_051), decimal("9000"),
                BigDecimal.ZERO, BigDecimal.ZERO, decimal("9000"), decimal("0.1"),
                BigDecimal.ZERO, false));

        List<BacktestEquityPoint> selected = new BacktestEquityPointSelector().select(points);

        assertThat(selected).hasSizeLessThanOrEqualTo(5_004);
        assertThat(selected).extracting(BacktestEquityPoint::pointTime)
                .contains(START, START.plusSeconds(3_050), START.plusSeconds(3_051),
                        START.plusSeconds(6_099));
    }

    private BacktestInstrumentInput instrument(long instrumentId, List<FuturesBar> marks) {
        List<FuturesBar> markets = List.of(bar(0, "100", "105", "95", "100"),
                bar(1, "101", "110", "100", "108"), bar(2, "109", "115", "105", "110"));
        return new BacktestInstrumentInput(instrumentId,
                new ContractTerms(decimal("0.1"), decimal("0.001"), BigDecimal.ONE),
                decimal("0.0002"), decimal("0.0006"), decimal("3"),
                List.of(new PositionTier(1, BigDecimal.ZERO, decimal("10000000"),
                        decimal("50"), decimal("0.005"))), markets, markets, marks,
                List.of(new FundingPoint(START.plusSeconds(120), decimal("0.0001"))));
    }

    private List<FuturesBar> normalMarkBars() {
        return List.of(bar(0, "100", "105", "95", "100"),
                bar(1, "101", "110", "100", "108"), bar(2, "109", "115", "105", "110"));
    }

    private FuturesBar bar(int index, String open, String high, String low, String close) {
        Instant time = START.plusSeconds(index * 60L);
        return new FuturesBar(time, time.plusSeconds(60), decimal(open), decimal(high),
                decimal(low), decimal(close), true);
    }

    private static StrategyDescriptor descriptor(String code) {
        return new StrategyDescriptor(code, "1", code, "Backtest test strategy", StrategyEngineType.JAVA,
                Set.of(DataCapability.MARKET_CANDLE, DataCapability.MARK_CANDLE,
                        DataCapability.FUNDING_RATE, DataCapability.POSITION_TIER),
                Set.of(BarInterval.M1), BarInterval.M1, PriceType.MARKET,
                "ON_BAR_CLOSE", "FIXED_FRACTION_10_PERCENT", decimal("3"), decimal("5"),
                "CONTRACT_RATE_V1", "FIXED_BPS_5", "NEXT_BAR_V1");
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private static class OpenThenCloseStrategy implements InvestmentStrategy {
        @Override
        public StrategyDescriptor descriptor() {
            return JavaBacktestEngineTests.descriptor("OPEN_CLOSE");
        }

        @Override
        public StrategyDecision evaluate(StrategyContext context) {
            StrategySignal signal = context.bars().size() == 1 ? StrategySignal.OPEN_LONG
                    : context.bars().size() == 2 ? StrategySignal.CLOSE_POSITION : StrategySignal.HOLD;
            return new StrategyDecision(signal, context.evaluationTime(), "fixed test decision");
        }
    }

    private static final class OpenThenHoldStrategy extends OpenThenCloseStrategy {
        @Override
        public StrategyDescriptor descriptor() {
            return JavaBacktestEngineTests.descriptor("OPEN_HOLD");
        }

        @Override
        public StrategyDecision evaluate(StrategyContext context) {
            return new StrategyDecision(context.bars().size() == 1 ? StrategySignal.OPEN_LONG : StrategySignal.HOLD,
                    context.evaluationTime(), "fixed test decision");
        }
    }
}
