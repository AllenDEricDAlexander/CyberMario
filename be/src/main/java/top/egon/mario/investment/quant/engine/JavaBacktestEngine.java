package top.egon.mario.investment.quant.engine;

import org.springframework.stereotype.Component;
import top.egon.mario.investment.common.model.OrderType;
import top.egon.mario.investment.common.model.PositionAction;
import top.egon.mario.investment.common.model.PositionSide;
import top.egon.mario.investment.portfolio.margin.FundingModel;
import top.egon.mario.investment.portfolio.margin.IsolatedMarginModel;
import top.egon.mario.investment.portfolio.margin.IsolatedPosition;
import top.egon.mario.investment.portfolio.margin.LiquidationModel;
import top.egon.mario.investment.portfolio.margin.PositionTier;
import top.egon.mario.investment.portfolio.margin.PositionTierResolver;
import top.egon.mario.investment.quant.backtest.model.BacktestEquityPoint;
import top.egon.mario.investment.quant.backtest.model.BacktestEvent;
import top.egon.mario.investment.quant.backtest.model.BacktestInput;
import top.egon.mario.investment.quant.backtest.model.BacktestInstrumentInput;
import top.egon.mario.investment.quant.backtest.model.BacktestMetrics;
import top.egon.mario.investment.quant.backtest.model.BacktestResult;
import top.egon.mario.investment.quant.backtest.model.BacktestTrade;
import top.egon.mario.investment.quant.backtest.model.FundingPoint;
import top.egon.mario.investment.quant.strategy.StrategyContext;
import top.egon.mario.investment.quant.strategy.StrategyDecision;
import top.egon.mario.investment.quant.strategy.StrategySignal;
import top.egon.mario.investment.trading.matching.BarMatchingModel;
import top.egon.mario.investment.trading.matching.FixedBpsSlippageModel;
import top.egon.mario.investment.trading.matching.RateFeeModel;
import top.egon.mario.investment.trading.matching.model.FuturesBar;
import top.egon.mario.investment.trading.matching.model.MatchResult;
import top.egon.mario.investment.trading.matching.model.MatchStatus;
import top.egon.mario.investment.trading.matching.model.MatchingOrder;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Closed-bar Java strategy runtime with explicit N/N+1 evaluation and deterministic event ordering.
 */
@Component
public class JavaBacktestEngine implements BacktestEngine {

    private static final BigDecimal POSITION_FRACTION = new BigDecimal("0.10");
    private static final double SECONDS_PER_YEAR = 365.25d * 24d * 60d * 60d;
    private static final double MAX_STORED_RATIO = 999_999_999_999d;

    private final IsolatedMarginModel marginModel = new IsolatedMarginModel();
    private final FundingModel fundingModel = new FundingModel(marginModel);
    private final LiquidationModel liquidationModel = new LiquidationModel(marginModel);
    private final PositionTierResolver tierResolver = new PositionTierResolver();

    @Override
    public BacktestResult run(BacktestInput input) {
        Simulation simulation = new Simulation(input.initialEquity());
        Map<Long, InstrumentState> states = new HashMap<>();
        List<TimelineBar> timeline = new ArrayList<>();
        for (BacktestInstrumentInput instrument : input.instruments()) {
            states.put(instrument.instrumentId(), new InstrumentState(
                    instrument, input.strategy().descriptor().slippageModelCode()));
            for (int index = 0; index < instrument.marketBars().size(); index++) {
                timeline.add(new TimelineBar(instrument, index));
            }
        }
        timeline.sort(Comparator.comparing(TimelineBar::time)
                .thenComparingLong(value -> value.input().instrumentId()));
        int cursor = 0;
        while (cursor < timeline.size()) {
            Instant time = timeline.get(cursor).time();
            boolean eventPoint = false;
            while (cursor < timeline.size() && timeline.get(cursor).time().equals(time)) {
                TimelineBar item = timeline.get(cursor++);
                eventPoint |= processBar(input, simulation, states.get(item.input().instrumentId()), item.index());
            }
            simulation.addEquityPoint(time, states.values(), eventPoint, marginModel);
        }
        List<BacktestEvent> events = orderedEvents(simulation.events);
        BacktestMetrics metrics = metrics(input.initialEquity(), simulation, events);
        return new BacktestResult(metrics, simulation.trades, events, simulation.equityPoints);
    }

    private boolean processBar(BacktestInput input, Simulation simulation,
                               InstrumentState state, int index) {
        FuturesBar marketBar = state.input.marketBars().get(index);
        FuturesBar signalBar = state.input.signalBars().get(index);
        FuturesBar markBar = state.input.markBars().get(index);
        boolean eventPoint = false;
        if (state.pending != null) {
            eventPoint |= executePending(simulation, state, marketBar);
        }
        eventPoint |= applyFunding(simulation, state, markBar);
        eventPoint |= liquidateIfRequired(simulation, state, markBar);
        List<FuturesBar> visibleBars = state.input.signalBars().subList(0, index + 1);
        StrategyDecision decision = input.strategy().evaluate(new StrategyContext(
                state.input.instrumentId(), signalBar.closeTime(), signalBar.closeTime(),
                visibleBars, state.position == null ? null : state.position.side));
        if (!decision.signalTime().equals(signalBar.closeTime())) {
            throw new IllegalArgumentException("Strategy decision must use the current closed-bar time");
        }
        scheduleDecision(simulation, state, decision, signalBar);
        state.lastMarkPrice = markBar.close();
        return eventPoint;
    }

    private void scheduleDecision(Simulation simulation, InstrumentState state,
                                  StrategyDecision decision, FuturesBar signalBar) {
        if (state.pending != null || decision.signal() == StrategySignal.HOLD) {
            return;
        }
        if (decision.signal() == StrategySignal.CLOSE_POSITION) {
            if (state.position != null) {
                state.pending = new PendingOrder(state.position.side, PositionAction.CLOSE,
                        state.position.quantity, signalBar.closeTime());
            }
            return;
        }
        if (state.position != null) {
            return;
        }
        PositionSide side = decision.signal() == StrategySignal.OPEN_LONG ? PositionSide.LONG : PositionSide.SHORT;
        BigDecimal targetNotional = simulation.wallet.multiply(POSITION_FRACTION)
                .multiply(state.input.leverage());
        BigDecimal quantity = targetNotional.divide(signalBar.close()
                .multiply(state.input.contractTerms().contractMultiplier()), MathContext.DECIMAL128);
        state.pending = new PendingOrder(side, PositionAction.OPEN, quantity, signalBar.closeTime());
    }

    private boolean executePending(Simulation simulation, InstrumentState state, FuturesBar bar) {
        PendingOrder pending = state.pending;
        BarMatchingModel matcher = new BarMatchingModel(
                new FixedBpsSlippageModel(slippageBps(state)),
                new RateFeeModel(state.input.makerFeeRate(), state.input.takerFeeRate()));
        MatchingOrder order = new MatchingOrder(simulation.nextOrderId++, OrderType.MARKET,
                pending.side, pending.action, pending.quantity, null, pending.eligibleAfter);
        MatchResult result = matcher.match(order, bar, state.input.contractTerms());
        if (result.status() != MatchStatus.FILLED) {
            return false;
        }
        state.pending = null;
        simulation.wallet = simulation.wallet.subtract(result.fee());
        simulation.totalFee = simulation.totalFee.add(result.fee());
        simulation.turnover = simulation.turnover.add(result.notional());
        simulation.event(state.input.instrumentId(), "FILL", result.marketBarOpenTime(), result.notional(),
                Map.of("action", pending.action.name(), "side", pending.side.name(),
                        "price", result.fillPrice().toPlainString(), "quantity", result.quantity().toPlainString()));
        simulation.event(state.input.instrumentId(), "FEE", result.marketBarOpenTime(), result.fee().negate(),
                Map.of("liquidityRole", result.liquidityRole().name()));
        if (pending.action == PositionAction.OPEN) {
            BigDecimal margin = marginModel.initialMargin(result.notional(), state.input.leverage());
            state.position = new PositionState(pending.side, result.marketBarOpenTime(), result.fillPrice(),
                    result.quantity(), state.input.leverage(), margin, result.fee());
        } else {
            closePosition(simulation, state, result.marketBarOpenTime(), result.fillPrice(), result.fee(), "SIGNAL");
        }
        return true;
    }

    private boolean applyFunding(Simulation simulation, InstrumentState state, FuturesBar markBar) {
        if (state.position == null) {
            return false;
        }
        boolean applied = false;
        while (state.fundingCursor < state.input.fundingPoints().size()) {
            FundingPoint point = state.input.fundingPoints().get(state.fundingCursor);
            if (point.fundingTime().isAfter(markBar.closeTime())) {
                break;
            }
            state.fundingCursor++;
            if (!point.fundingTime().isAfter(state.position.entryTime)) {
                continue;
            }
            BigDecimal cashFlow = fundingModel.calculate(state.position.side, state.position.quantity,
                    markBar.close(), state.input.contractTerms().contractMultiplier(), point.rate()).cashFlow();
            simulation.wallet = simulation.wallet.add(cashFlow);
            simulation.totalFunding = simulation.totalFunding.add(cashFlow);
            state.position.funding = state.position.funding.add(cashFlow);
            simulation.event(state.input.instrumentId(), "FUNDING", point.fundingTime(), cashFlow,
                    Map.of("rate", point.rate().toPlainString()));
            applied = true;
        }
        return applied;
    }

    private boolean liquidateIfRequired(Simulation simulation, InstrumentState state, FuturesBar markBar) {
        if (state.position == null) {
            return false;
        }
        BigDecimal adversePrice = state.position.side == PositionSide.LONG ? markBar.low() : markBar.high();
        BigDecimal notional = marginModel.notional(state.position.quantity, adversePrice,
                state.input.contractTerms().contractMultiplier());
        PositionTier tier = tierResolver.resolve(notional, state.input.positionTiers());
        boolean liquidate = liquidationModel.evaluate(new IsolatedPosition(
                        state.position.side, state.position.quantity, state.position.entryPrice,
                        state.position.margin), adversePrice, state.input.contractTerms().contractMultiplier(),
                tier, state.input.takerFeeRate()).liquidationRequired();
        if (!liquidate) {
            return false;
        }
        BigDecimal fee = notional.multiply(state.input.takerFeeRate());
        simulation.wallet = simulation.wallet.subtract(fee);
        simulation.totalFee = simulation.totalFee.add(fee);
        simulation.turnover = simulation.turnover.add(notional);
        simulation.liquidationCount++;
        simulation.event(state.input.instrumentId(), "LIQUIDATION", markBar.closeTime(), null,
                Map.of("markPrice", adversePrice.toPlainString()));
        simulation.event(state.input.instrumentId(), "FEE", markBar.closeTime(), fee.negate(),
                Map.of("liquidityRole", "TAKER", "reason", "LIQUIDATION"));
        closePosition(simulation, state, markBar.closeTime(), adversePrice, fee, "LIQUIDATION");
        state.pending = null;
        return true;
    }

    private void closePosition(Simulation simulation, InstrumentState state, Instant exitTime,
                               BigDecimal exitPrice, BigDecimal closeFee, String reason) {
        PositionState position = state.position;
        BigDecimal grossPnl = marginModel.unrealizedPnl(position.side, position.entryPrice, exitPrice,
                position.quantity, state.input.contractTerms().contractMultiplier());
        simulation.wallet = simulation.wallet.add(grossPnl);
        BigDecimal fees = position.openFee.add(closeFee);
        BigDecimal net = grossPnl.subtract(fees).add(position.funding);
        simulation.trades.add(new BacktestTrade(state.input.instrumentId(), position.side,
                position.entryTime, exitTime, position.entryPrice, exitPrice, position.quantity,
                position.leverage, grossPnl, fees, position.funding, net, reason));
        state.position = null;
    }

    private BigDecimal slippageBps(InstrumentState state) {
        String code = state == null ? "" : state.slippageModelCode;
        int separator = code.lastIndexOf('_');
        if (separator < 0) {
            throw new IllegalArgumentException("Unsupported fixed slippage model: " + code);
        }
        try {
            return new BigDecimal(code.substring(separator + 1));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Unsupported fixed slippage model: " + code, exception);
        }
    }

    private List<BacktestEvent> orderedEvents(List<RawEvent> rawEvents) {
        List<RawEvent> sorted = rawEvents.stream().sorted(Comparator.comparing(RawEvent::time)
                .thenComparing(RawEvent::instrumentId, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(event -> eventPriority(event.type()))).toList();
        List<BacktestEvent> result = new ArrayList<>();
        for (int index = 0; index < sorted.size(); index++) {
            RawEvent event = sorted.get(index);
            result.add(new BacktestEvent(index + 1L, event.instrumentId, event.type, event.time,
                    event.amount, event.balanceAfter, event.details));
        }
        return List.copyOf(result);
    }

    private BacktestMetrics metrics(BigDecimal initialEquity, Simulation simulation,
                                    List<BacktestEvent> events) {
        BigDecimal finalEquity = simulation.equityPoints.getLast().equity();
        BigDecimal totalReturn = finalEquity.subtract(initialEquity)
                .divide(initialEquity, MathContext.DECIMAL128);
        long wins = simulation.trades.stream().filter(trade -> trade.netPnl().signum() > 0).count();
        BigDecimal winRate = simulation.trades.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(simulation.trades.size()), MathContext.DECIMAL128);
        BigDecimal profit = simulation.trades.stream().map(BacktestTrade::netPnl)
                .filter(value -> value.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal loss = simulation.trades.stream().map(BacktestTrade::netPnl)
                .filter(value -> value.signum() < 0).map(BigDecimal::abs).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal profitFactor = loss.signum() == 0 ? (profit.signum() == 0 ? BigDecimal.ZERO : profit)
                : profit.divide(loss, MathContext.DECIMAL128);
        BigDecimal maxDrawdown = simulation.equityPoints.stream().map(BacktestEquityPoint::drawdown)
                .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        List<Double> periodicReturns = periodicReturns(simulation.equityPoints);
        BigDecimal annualizedReturn = annualizedReturn(initialEquity, finalEquity, simulation.equityPoints);
        BigDecimal sharpeRatio = riskAdjustedRatio(periodicReturns, simulation.equityPoints, false);
        BigDecimal sortinoRatio = riskAdjustedRatio(periodicReturns, simulation.equityPoints, true);
        return new BacktestMetrics(totalReturn, annualizedReturn, maxDrawdown, sharpeRatio, sortinoRatio,
                winRate, profitFactor, simulation.turnover, simulation.trades.size(), simulation.totalFee,
                simulation.totalFunding, simulation.liquidationCount);
    }

    private BigDecimal annualizedReturn(BigDecimal initialEquity, BigDecimal finalEquity,
                                        List<BacktestEquityPoint> points) {
        long seconds = Duration.between(points.getFirst().pointTime(), points.getLast().pointTime()).toSeconds();
        if (seconds <= 0 || finalEquity.signum() <= 0) {
            return finalEquity.signum() <= 0 ? BigDecimal.ONE.negate() : BigDecimal.ZERO;
        }
        double growth = finalEquity.divide(initialEquity, MathContext.DECIMAL128).doubleValue();
        return storedRatio(StrictMath.pow(growth, SECONDS_PER_YEAR / seconds) - 1d);
    }

    private BigDecimal riskAdjustedRatio(List<Double> returns, List<BacktestEquityPoint> points,
                                         boolean downsideOnly) {
        if (returns.size() < 2) {
            return BigDecimal.ZERO;
        }
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0d);
        double variance = returns.stream().mapToDouble(value -> {
            double deviation = downsideOnly ? StrictMath.min(value, 0d) : value - mean;
            return deviation * deviation;
        }).average().orElse(0d);
        if (variance == 0d) {
            return BigDecimal.ZERO;
        }
        long seconds = Duration.between(points.getFirst().pointTime(), points.getLast().pointTime()).toSeconds();
        double averageStepSeconds = seconds <= 0 ? 0d : (double) seconds / returns.size();
        if (averageStepSeconds <= 0d) {
            return BigDecimal.ZERO;
        }
        double annualization = StrictMath.sqrt(SECONDS_PER_YEAR / averageStepSeconds);
        return storedRatio(mean / StrictMath.sqrt(variance) * annualization);
    }

    private List<Double> periodicReturns(List<BacktestEquityPoint> points) {
        List<Double> result = new ArrayList<>();
        for (int index = 1; index < points.size(); index++) {
            BigDecimal previous = points.get(index - 1).equity();
            if (previous.signum() > 0) {
                result.add(points.get(index).equity().divide(previous, MathContext.DECIMAL128)
                        .subtract(BigDecimal.ONE).doubleValue());
            }
        }
        return List.copyOf(result);
    }

    private BigDecimal storedRatio(double value) {
        if (!Double.isFinite(value)) {
            return BigDecimal.valueOf(value < 0d ? -MAX_STORED_RATIO : MAX_STORED_RATIO);
        }
        double bounded = StrictMath.max(-MAX_STORED_RATIO, StrictMath.min(MAX_STORED_RATIO, value));
        return BigDecimal.valueOf(bounded).setScale(12, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private static int eventPriority(String type) {
        return switch (type) {
            case "FILL" -> 10;
            case "FEE" -> 20;
            case "FUNDING" -> 30;
            case "LIQUIDATION" -> 40;
            default -> 100;
        };
    }

    private static final class Simulation {
        private BigDecimal wallet;
        private BigDecimal peakEquity;
        private BigDecimal totalFee = BigDecimal.ZERO;
        private BigDecimal totalFunding = BigDecimal.ZERO;
        private BigDecimal turnover = BigDecimal.ZERO;
        private long liquidationCount;
        private long nextOrderId = 1;
        private final List<BacktestTrade> trades = new ArrayList<>();
        private final List<RawEvent> events = new ArrayList<>();
        private final List<BacktestEquityPoint> equityPoints = new ArrayList<>();

        private Simulation(BigDecimal initialEquity) {
            this.wallet = initialEquity;
            this.peakEquity = initialEquity;
        }

        private void event(long instrumentId, String type, Instant time,
                           BigDecimal amount, Map<String, Object> details) {
            events.add(new RawEvent(instrumentId, type, time, amount, wallet, details));
        }

        private void addEquityPoint(Instant time, Iterable<InstrumentState> states,
                                    boolean eventPoint, IsolatedMarginModel marginModel) {
            BigDecimal usedMargin = BigDecimal.ZERO;
            BigDecimal unrealized = BigDecimal.ZERO;
            BigDecimal exposure = BigDecimal.ZERO;
            for (InstrumentState state : states) {
                if (state.position == null || state.lastMarkPrice == null) {
                    continue;
                }
                usedMargin = usedMargin.add(state.position.margin);
                unrealized = unrealized.add(marginModel.unrealizedPnl(state.position.side,
                        state.position.entryPrice, state.lastMarkPrice, state.position.quantity,
                        state.input.contractTerms().contractMultiplier()));
                exposure = exposure.add(marginModel.notional(state.position.quantity, state.lastMarkPrice,
                        state.input.contractTerms().contractMultiplier()));
            }
            BigDecimal equity = wallet.add(unrealized);
            peakEquity = peakEquity.max(equity);
            BigDecimal drawdown = peakEquity.signum() == 0 ? BigDecimal.ZERO
                    : peakEquity.subtract(equity).max(BigDecimal.ZERO)
                    .divide(peakEquity, MathContext.DECIMAL128);
            equityPoints.add(new BacktestEquityPoint(time, wallet, usedMargin, unrealized,
                    equity, drawdown, exposure, eventPoint));
        }
    }

    private static final class InstrumentState {
        private final BacktestInstrumentInput input;
        private final String slippageModelCode;
        private PendingOrder pending;
        private PositionState position;
        private int fundingCursor;
        private BigDecimal lastMarkPrice;

        private InstrumentState(BacktestInstrumentInput input, String slippageModelCode) {
            this.input = input;
            this.slippageModelCode = slippageModelCode;
        }
    }

    private static final class PositionState {
        private final PositionSide side;
        private final Instant entryTime;
        private final BigDecimal entryPrice;
        private final BigDecimal quantity;
        private final BigDecimal leverage;
        private final BigDecimal margin;
        private final BigDecimal openFee;
        private BigDecimal funding = BigDecimal.ZERO;

        private PositionState(PositionSide side, Instant entryTime, BigDecimal entryPrice,
                              BigDecimal quantity, BigDecimal leverage,
                              BigDecimal margin, BigDecimal openFee) {
            this.side = side;
            this.entryTime = entryTime;
            this.entryPrice = entryPrice;
            this.quantity = quantity;
            this.leverage = leverage;
            this.margin = margin;
            this.openFee = openFee;
        }
    }

    private record PendingOrder(PositionSide side, PositionAction action,
                                BigDecimal quantity, Instant eligibleAfter) {
    }

    private record TimelineBar(BacktestInstrumentInput input, int index) {
        private Instant time() {
            return input.marketBars().get(index).closeTime();
        }
    }

    private record RawEvent(Long instrumentId, String type, Instant time,
                            BigDecimal amount, BigDecimal balanceAfter,
                            Map<String, Object> details) {
    }
}
