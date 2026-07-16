package top.egon.mario.investment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import top.egon.mario.investment.agent.model.InvestmentAgentAction;
import top.egon.mario.investment.agent.model.InvestmentAgentRunInput;
import top.egon.mario.investment.agent.model.InvestmentAgentRunType;
import top.egon.mario.investment.agent.service.InvestmentAgentDecisionValidator;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.OrderType;
import top.egon.mario.investment.common.model.PositionAction;
import top.egon.mario.investment.common.model.PositionSide;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.common.model.ProductType;
import top.egon.mario.investment.marketdata.provider.ContractCandleProvider;
import top.egon.mario.investment.marketdata.provider.ProviderRegistry;
import top.egon.mario.investment.marketdata.provider.model.CandleQuery;
import top.egon.mario.investment.marketdata.provider.model.ExternalCandle;
import top.egon.mario.investment.marketdata.query.InvestmentMarketQueryService;
import top.egon.mario.investment.marketdata.subscription.InvestmentMarketSubscriptionRegistry;
import top.egon.mario.investment.marketdata.subscription.MarketSubscription;
import top.egon.mario.investment.marketdata.subscription.RetentionPolicy;
import top.egon.mario.investment.marketdata.subscription.SubscriptionSchedule;
import top.egon.mario.investment.marketdata.web.dto.InvestmentCandleResponse;
import top.egon.mario.investment.portfolio.margin.PositionTier;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskContext;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskLimits;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskService;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskSource;
import top.egon.mario.investment.portfolio.risk.rule.ExposureRiskRule;
import top.egon.mario.investment.portfolio.risk.rule.LossRiskRule;
import top.egon.mario.investment.portfolio.risk.rule.MarketRiskRule;
import top.egon.mario.investment.portfolio.risk.rule.OrderRiskRule;
import top.egon.mario.investment.portfolio.risk.rule.SwitchRiskRule;
import top.egon.mario.investment.quant.backtest.model.BacktestInput;
import top.egon.mario.investment.quant.backtest.model.BacktestInstrumentInput;
import top.egon.mario.investment.quant.dataset.InvestmentDatasetHasher;
import top.egon.mario.investment.quant.engine.JavaBacktestEngine;
import top.egon.mario.investment.quant.strategy.InvestmentStrategy;
import top.egon.mario.investment.quant.strategy.InvestmentStrategyRegistry;
import top.egon.mario.investment.quant.strategy.StrategyContext;
import top.egon.mario.investment.quant.strategy.StrategyDecision;
import top.egon.mario.investment.quant.strategy.StrategyDescriptor;
import top.egon.mario.investment.quant.strategy.StrategyEngineType;
import top.egon.mario.investment.quant.strategy.StrategySignal;
import top.egon.mario.investment.research.indicator.InvestmentIndicatorService;
import top.egon.mario.investment.research.indicator.Ta4jIndicatorAdapter;
import top.egon.mario.investment.research.report.GeneratedResearchReport;
import top.egon.mario.investment.trading.matching.BarMatchingModel;
import top.egon.mario.investment.trading.matching.FixedBpsSlippageModel;
import top.egon.mario.investment.trading.matching.RateFeeModel;
import top.egon.mario.investment.trading.matching.model.ContractTerms;
import top.egon.mario.investment.trading.matching.model.FuturesBar;
import top.egon.mario.investment.trading.matching.model.MatchResult;
import top.egon.mario.investment.trading.matching.model.MatchStatus;
import top.egon.mario.investment.trading.matching.model.MatchingOrder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises one deterministic fixture from code subscription through both paper-trading entry points.
 */
class InvestmentVerticalFlowTests {

    private static final long INSTRUMENT_ID = 11L;
    private static final Instant START = Instant.parse("2035-01-01T00:00:00Z");
    private static final Instant DATA_AS_OF = START.plus(Duration.ofMinutes(40));

    @Test
    void fixtureProviderFlowsThroughAnalysisBacktestManualPaperTradeAndAgentPaperTrade() {
        FixtureCandleProvider provider = new FixtureCandleProvider();
        ProviderRegistry providerRegistry = new ProviderRegistry(List.of(provider));
        MarketSubscription subscription = subscription();
        InvestmentMarketSubscriptionRegistry subscriptionRegistry = new InvestmentMarketSubscriptionRegistry(
                List.of(() -> List.of(subscription)), providerRegistry);
        FixedFlowStrategy strategy = new FixedFlowStrategy();
        InvestmentStrategyRegistry strategyRegistry = new InvestmentStrategyRegistry(
                List.of(strategy), subscriptionRegistry);

        CandleQuery query = new CandleQuery(ProductType.USDT_FUTURES, "BTCUSDT", PriceType.MARK,
                BarInterval.M1, START, DATA_AS_OF, 100);
        List<ExternalCandle> ingested = provider.candles(query);
        List<InvestmentCandleResponse> marketView = ingested.stream()
                .map(this::marketView)
                .toList();
        String datasetHash = new InvestmentDatasetHasher(new ObjectMapper().findAndRegisterModules())
                .hash(marketView);

        InvestmentMarketQueryService marketQuery = mock(InvestmentMarketQueryService.class);
        when(marketQuery.candles(INSTRUMENT_ID, PriceType.MARK, BarInterval.M1,
                START, DATA_AS_OF, DATA_AS_OF, 2_000)).thenReturn(marketView);
        var indicators = new InvestmentIndicatorService(marketQuery, new Ta4jIndicatorAdapter())
                .calculate(INSTRUMENT_ID, PriceType.MARK, BarInterval.M1, START, DATA_AS_OF, DATA_AS_OF);
        GeneratedResearchReport report = new GeneratedResearchReport(
                "BTCUSDT fixture report", "fixed closed-bar analysis",
                "# BTCUSDT\n\nData as of " + DATA_AS_OF,
                Map.of("close", indicators.points().getLast().close(), "datasetHash", datasetHash),
                indicators, List.of());

        List<FuturesBar> bars = ingested.stream().map(this::futuresBar).toList();
        BacktestInstrumentInput instrument = new BacktestInstrumentInput(
                INSTRUMENT_ID, terms(), decimal("0.0002"), decimal("0.0006"), decimal("2"),
                List.of(new PositionTier(1, BigDecimal.ZERO, decimal("1000000"),
                        decimal("20"), decimal("0.005"))),
                bars, bars, bars, List.of());
        var backtest = new JavaBacktestEngine().run(new BacktestInput(
                strategyRegistry.require("FIXTURE_FLOW"), decimal("10000"), List.of(instrument)));

        PaperFlowLedger paper = new PaperFlowLedger(riskService(), terms());
        MatchResult manualFill = paper.submit(InvestmentRiskSource.USER, PositionSide.LONG,
                bars.get(0).closeTime(), bars.get(1));

        InvestmentAgentRunInput agentInput = new InvestmentAgentRunInput(
                5L, 7L, 31L, InvestmentAgentRunType.AUTO_TRADE, List.of(INSTRUMENT_ID), DATA_AS_OF);
        String fixedModelOutput = """
                {"instrumentId":11,"action":"OPEN_SHORT","confidence":"0.75","horizon":"INTRADAY",
                 "thesis":"fixture trend","risks":["volatility"],"invalidation":["breakout"],
                 "requestedQuantity":"1","requestedNotional":"100","requestedLeverage":"2",
                 "orderType":"MARKET","limitPrice":null,"dataAsOf":"2035-01-01T00:40:00Z","expiresAt":null}
                """;
        var agentDecision = new InvestmentAgentDecisionValidator(
                Clock.fixed(DATA_AS_OF.plusSeconds(30), ZoneOffset.UTC))
                .validate(fixedModelOutput, agentInput);
        MatchResult agentFill = paper.submit(InvestmentRiskSource.AGENT, PositionSide.SHORT,
                bars.get(1).closeTime(), bars.get(2));

        assertThat(subscriptionRegistry.subscriptions()).containsExactly(subscription);
        assertThat(ingested).hasSize(40).allMatch(ExternalCandle::closed);
        assertThat(indicators.dataAsOf()).isEqualTo(DATA_AS_OF);
        assertThat(indicators.inputHash()).hasSize(64);
        assertThat(report.metrics()).containsEntry("datasetHash", datasetHash);
        assertThat(backtest.trades()).singleElement();
        assertThat(manualFill.status()).isEqualTo(MatchStatus.FILLED);
        assertThat(agentDecision.action()).isEqualTo(InvestmentAgentAction.OPEN_SHORT);
        assertThat(agentDecision.dataAsOf()).isEqualTo(DATA_AS_OF);
        assertThat(agentFill.status()).isEqualTo(MatchStatus.FILLED);
        assertThat(paper.marginLedger()).extracting(PaperEffect::source)
                .containsExactly(InvestmentRiskSource.USER, InvestmentRiskSource.AGENT);
        assertThat(paper.positions())
                .containsEntry(InvestmentRiskSource.USER, PositionSide.LONG)
                .containsEntry(InvestmentRiskSource.AGENT, PositionSide.SHORT);
    }

    private InvestmentRiskService riskService() {
        return new InvestmentRiskService(List.of(
                new SwitchRiskRule(), new MarketRiskRule(), new OrderRiskRule(),
                new ExposureRiskRule(), new LossRiskRule()),
                Clock.fixed(DATA_AS_OF.plusSeconds(30), ZoneOffset.UTC));
    }

    private MarketSubscription subscription() {
        return new MarketSubscription("FIXTURE", ProductType.USDT_FUTURES, "BTCUSDT",
                Set.of(BarInterval.M1), Set.of(PriceType.MARK), Set.of(DataCapability.MARK_CANDLE),
                new SubscriptionSchedule(Map.of(DataCapability.MARK_CANDLE, Duration.ofMinutes(1)), Map.of()),
                new RetentionPolicy(Set.of(BarInterval.M1), Map.of()));
    }

    private InvestmentCandleResponse marketView(ExternalCandle candle) {
        return new InvestmentCandleResponse(candle.openTime(), candle.closeTime(),
                plain(candle.open()), plain(candle.high()), plain(candle.low()), plain(candle.close()),
                plain(candle.baseVolume()), plain(candle.quoteVolume()), candle.closed(), 1L, DATA_AS_OF);
    }

    private FuturesBar futuresBar(ExternalCandle candle) {
        return new FuturesBar(candle.openTime(), candle.closeTime(), candle.open(), candle.high(),
                candle.low(), candle.close(), candle.closed());
    }

    private static ContractTerms terms() {
        return new ContractTerms(decimal("0.1"), decimal("0.001"), BigDecimal.ONE);
    }

    private static InvestmentRiskLimits limits() {
        return new InvestmentRiskLimits(decimal("5"), decimal("1000"), decimal("2000"),
                decimal("3000"), 3L, decimal("500"), decimal("0.2"),
                100L, 0L, 300L, decimal("100"));
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static final class FixtureCandleProvider implements ContractCandleProvider {

        @Override
        public String providerCode() {
            return "FIXTURE";
        }

        @Override
        public Set<DataCapability> capabilities() {
            return Set.of(DataCapability.MARK_CANDLE);
        }

        @Override
        public List<ExternalCandle> candles(CandleQuery query) {
            List<ExternalCandle> candles = new ArrayList<>();
            for (int index = 0; index < 40; index++) {
                Instant openTime = START.plus(Duration.ofMinutes(index));
                BigDecimal open = decimal(Integer.toString(100 + index));
                BigDecimal close = open.add(BigDecimal.ONE);
                candles.add(new ExternalCandle("FIXTURE", ProductType.USDT_FUTURES, "BTCUSDT",
                        PriceType.MARK, BarInterval.M1, openTime, openTime.plusSeconds(60),
                        open, close.add(BigDecimal.ONE), open.subtract(BigDecimal.ONE), close,
                        decimal("10"), decimal("1000"), true, DATA_AS_OF));
            }
            return List.copyOf(candles);
        }
    }

    private static final class FixedFlowStrategy implements InvestmentStrategy {

        private static final StrategyDescriptor DESCRIPTOR = new StrategyDescriptor(
                "FIXTURE_FLOW", "1.0.0", "Fixture flow", "Deterministic integration strategy",
                StrategyEngineType.JAVA, Set.of(DataCapability.MARK_CANDLE), Set.of(BarInterval.M1),
                BarInterval.M1, PriceType.MARK, "ON_BAR_CLOSE", "FIXED_FRACTION_10_PERCENT",
                decimal("2"), decimal("5"), "CONTRACT_RATE_V1", "FIXED_BPS_5", "NEXT_BAR_V1");

        @Override
        public StrategyDescriptor descriptor() {
            return DESCRIPTOR;
        }

        @Override
        public StrategyDecision evaluate(StrategyContext context) {
            StrategySignal signal = context.bars().size() == 1 ? StrategySignal.OPEN_LONG
                    : context.bars().size() == 2 ? StrategySignal.CLOSE_POSITION : StrategySignal.HOLD;
            return new StrategyDecision(signal, context.evaluationTime(), "fixed fixture decision");
        }
    }

    private static final class PaperFlowLedger {

        private final InvestmentRiskService riskService;
        private final ContractTerms contractTerms;
        private final List<PaperEffect> marginLedger = new ArrayList<>();
        private final Map<InvestmentRiskSource, PositionSide> positions =
                new EnumMap<>(InvestmentRiskSource.class);

        private PaperFlowLedger(InvestmentRiskService riskService, ContractTerms contractTerms) {
            this.riskService = riskService;
            this.contractTerms = contractTerms;
        }

        MatchResult submit(InvestmentRiskSource source, PositionSide side,
                           Instant eligibleAfter, FuturesBar executionBar) {
            InvestmentRiskContext context = new InvestmentRiskContext(source,
                    new InvestmentRiskContext.AccountState(true, true),
                    new InvestmentRiskContext.MarketState(true, true, 1L, executionBar.open(), true, true),
                    new InvestmentRiskContext.OrderState(decimal("100"), decimal("2"), 0L,
                            null, decimal("50"), decimal("5"), false, true),
                    new InvestmentRiskContext.PortfolioState(decimal("100"), decimal("100"), 1L,
                            BigDecimal.ZERO, BigDecimal.ZERO, decimal("10000")),
                    limits(), null);
            assertThat(riskService.evaluate(context).passed()).isTrue();
            MatchingOrder order = new MatchingOrder(marginLedger.size() + 1L, OrderType.MARKET,
                    side, PositionAction.OPEN, BigDecimal.ONE, null, eligibleAfter);
            MatchResult result = new BarMatchingModel(
                    new FixedBpsSlippageModel(decimal("5")),
                    new RateFeeModel(decimal("0.0002"), decimal("0.0006")))
                    .match(order, executionBar, contractTerms);
            if (result.status() == MatchStatus.FILLED) {
                marginLedger.add(new PaperEffect(source, result));
                positions.put(source, side);
            }
            return result;
        }

        List<PaperEffect> marginLedger() {
            return List.copyOf(marginLedger);
        }

        Map<InvestmentRiskSource, PositionSide> positions() {
            return Map.copyOf(positions);
        }
    }

    private record PaperEffect(InvestmentRiskSource source, MatchResult result) {
    }
}
