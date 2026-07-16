package top.egon.mario.investment.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;
import top.egon.mario.agent.service.model.ScopedAgentToolSet;
import top.egon.mario.investment.agent.service.InvestmentAgentPresetRegistry;
import top.egon.mario.investment.agent.service.InvestmentAgentToolCallbackFactory;
import top.egon.mario.investment.agent.tool.InvestmentAgentToolScope;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.marketdata.query.InvestmentMarketQueryService;
import top.egon.mario.investment.portfolio.query.InvestmentPortfolioQueryService;
import top.egon.mario.investment.portfolio.service.InvestmentPaperAccountService;
import top.egon.mario.investment.quant.backtest.InvestmentBacktestService;
import top.egon.mario.investment.quant.repository.InvestmentBacktestRunRepository;
import top.egon.mario.investment.research.indicator.InvestmentIndicatorService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestmentAgentToolTests {

    private static final Instant CUTOFF = Instant.parse("2035-01-01T00:00:00Z");

    @Mock
    private InvestmentMarketQueryService marketQueryService;
    @Mock
    private InvestmentIndicatorService indicatorService;
    @Mock
    private InvestmentPortfolioQueryService portfolioQueryService;
    @Mock
    private InvestmentPaperAccountService accountService;
    @Mock
    private InvestmentBacktestService backtestService;
    @Mock
    private InvestmentBacktestRunRepository backtestRunRepository;

    private InvestmentAgentToolCallbackFactory factory;

    @BeforeEach
    void setUp() {
        factory = new InvestmentAgentToolCallbackFactory(
                marketQueryService, indicatorService, portfolioQueryService, accountService,
                backtestService, backtestRunRepository);
    }

    @Test
    void exposesExactlyEightReadCallbacksWithoutSecurityBoundaryArgumentsOrWriteTools() {
        ScopedAgentToolSet tools = factory.create(scope(31L));
        Map<String, ToolCallback> callbacks = callbacks(tools);

        assertThat(callbacks.keySet()).containsExactlyInAnyOrderElementsOf(
                InvestmentAgentPresetRegistry.READ_TOOL_NAMES);
        assertThat(callbacks.keySet()).noneMatch(name -> name.contains("submit") || name.contains("write"));
        callbacks.values().forEach(callback -> assertThat(callback.getToolDefinition().inputSchema())
                .doesNotContain("actorId", "workspaceId", "accountId", "dataAsOf"));
    }

    @Test
    void candleToolUsesOnlyTheClosureCutoff() {
        Instant from = CUTOFF.minusSeconds(3_600);
        when(marketQueryService.candles(
                11L, PriceType.MARK, BarInterval.M1, from, CUTOFF, CUTOFF, 10)).thenReturn(List.of());
        ToolCallback callback = callbacks(factory.create(scope(31L))).get("get_investment_candles");

        assertThat(callback.call("""
                {"instrumentId":11,"priceType":"MARK","interval":"M1",
                 "fromInclusive":"2034-12-31T23:00:00Z","toExclusive":"2035-01-01T00:00:00Z","limit":10}
                """)).isEqualTo("[]");

        verify(marketQueryService).candles(
                11L, PriceType.MARK, BarInterval.M1, from, CUTOFF, CUTOFF, 10);
    }

    @Test
    void rejectsCrossInstrumentAndFutureWindowArgumentsBeforeQueryingData() {
        ToolCallback snapshot = callbacks(factory.create(scope(31L))).get("get_investment_market_snapshot");
        ToolCallback candles = callbacks(factory.create(scope(31L))).get("get_investment_candles");

        assertThatThrownBy(() -> snapshot.call("{\"instrumentId\":999}"))
                .hasMessageContaining("outside the server-bound Agent scope");
        assertThatThrownBy(() -> candles.call("""
                {"instrumentId":11,"priceType":"MARK","interval":"M1",
                 "fromInclusive":"2035-01-01T00:00:00Z","toExclusive":"2035-01-01T00:01:00Z","limit":10}
                """))
                .hasMessageContaining("exceeds the server-bound dataAsOf");
    }

    @Test
    void accountToolsCannotAcceptAnotherAccountAndFailClosedWhenNoAccountWasBound() {
        Map<String, ToolCallback> callbacks = callbacks(factory.create(scope(null)));

        assertThat(callbacks.get("get_investment_portfolio").getToolDefinition().inputSchema())
                .doesNotContain("accountId");
        assertThatThrownBy(() -> callbacks.get("get_investment_risk_state").call("{}"))
                .hasMessageContaining("no server-bound paper account");
    }

    private static InvestmentAgentToolScope scope(Long accountId) {
        return new InvestmentAgentToolScope(5L, 7L, accountId, List.of(11L, 12L), CUTOFF);
    }

    private static Map<String, ToolCallback> callbacks(ScopedAgentToolSet tools) {
        return tools.callbacks().stream().collect(Collectors.toMap(
                callback -> callback.getToolDefinition().name(), Function.identity()));
    }
}
