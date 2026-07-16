package top.egon.mario.investment.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.marketdata.query.InvestmentMarketQueryService;
import top.egon.mario.investment.marketdata.web.dto.InvestmentInstrumentDetailResponse;
import top.egon.mario.investment.marketdata.web.dto.InvestmentMarketOverviewResponse;
import top.egon.mario.investment.research.indicator.InvestmentIndicatorPoint;
import top.egon.mario.investment.research.indicator.InvestmentIndicatorService;
import top.egon.mario.investment.research.indicator.InvestmentIndicatorSnapshot;
import top.egon.mario.investment.research.indicator.ResearchHashSupport;
import top.egon.mario.investment.research.report.FrozenResearchReportInput;
import top.egon.mario.investment.research.report.InvestmentReportType;
import top.egon.mario.investment.research.report.InvestmentResearchReportGeneratorRegistry;
import top.egon.mario.investment.research.report.ResearchEvidenceSource;
import top.egon.mario.investment.research.report.ResearchEvidenceSourceService;
import top.egon.mario.investment.research.report.ResearchReportGenerationContext;
import top.egon.mario.investment.research.report.generator.InstrumentAnalysisReportGenerator;
import top.egon.mario.investment.research.report.generator.MarketOverviewReportGenerator;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the two Phase-1 generators only narrate deterministic service facts.
 */
class MarketResearchReportGeneratorTests {

    private static final Instant NOW = Instant.parse("2030-02-01T00:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void marketOverviewUsesOnlyTheFrozenMarketServiceResult() {
        InvestmentMarketQueryService market = mock(InvestmentMarketQueryService.class);
        ResearchEvidenceSourceService sources = mock(ResearchEvidenceSourceService.class);
        when(market.overview(NOW)).thenReturn(new InvestmentMarketOverviewResponse(8, 5, 3, 2, NOW));
        when(sources.requireMarketSources()).thenReturn(List.of(
                new ResearchEvidenceSource(9L, 42L, "BITGET", "BTCUSDT")));
        MarketOverviewReportGenerator generator = new MarketOverviewReportGenerator(market, sources, objectMapper);
        FrozenResearchReportInput input = new FrozenResearchReportInput(
                InvestmentReportType.MARKET_OVERVIEW, null, null, null, null, null, NOW);

        var generated = generator.generate(context(31L, input));

        assertThat(generated.metrics()).containsEntry("subscribedInstrumentCount", 8L)
                .containsEntry("freshQuoteCount", 5L)
                .containsEntry("staleOrMissingQuoteCount", 3L)
                .containsEntry("openQualityIssueCount", 2L);
        assertThat(generated.contentMarkdown()).contains("代码订阅标的：8", "新鲜报价：5")
                .doesNotContain("<", ">");
        assertThat(generated.evidence()).singleElement().satisfies(evidence -> {
            assertThat(evidence.sourceId()).isEqualTo(9L);
            assertThat(evidence.instrumentId()).isEqualTo(42L);
            assertThat(evidence.dataAsOf()).isEqualTo(NOW);
            assertThat(evidence.payloadHash()).hasSize(64);
        });
        verify(market).overview(NOW);
    }

    @Test
    void instrumentAnalysisCopiesIndicatorSnapshotAndEvidenceCutoff() {
        InvestmentMarketQueryService market = mock(InvestmentMarketQueryService.class);
        InvestmentIndicatorService indicators = mock(InvestmentIndicatorService.class);
        ResearchEvidenceSourceService sources = mock(ResearchEvidenceSourceService.class);
        FrozenResearchReportInput input = new FrozenResearchReportInput(
                InvestmentReportType.INSTRUMENT_ANALYSIS, 42L, PriceType.MARK, BarInterval.H1,
                NOW.minusSeconds(7_200), NOW.minusSeconds(3_600), NOW);
        InvestmentIndicatorPoint point = new InvestmentIndicatorPoint(
                NOW.minusSeconds(7_200), "50000.01", "49000", "49500", "61.2", "120", "100", "20",
                "51000", "49000", "47000", "900");
        InvestmentIndicatorSnapshot snapshot = new InvestmentIndicatorSnapshot(
                42L, PriceType.MARK, BarInterval.H1, NOW.minusSeconds(7_200), NOW.minusSeconds(3_600), NOW,
                "d".repeat(64), List.of(7L), List.of(point));
        when(market.instrument(42L)).thenReturn(instrument());
        when(indicators.calculate(42L, PriceType.MARK, BarInterval.H1,
                input.fromInclusive(), input.toExclusive(), NOW)).thenReturn(snapshot);
        when(sources.requireInstrumentSource(42L))
                .thenReturn(new ResearchEvidenceSource(9L, 42L, "BITGET", "BTCUSDT"));
        InstrumentAnalysisReportGenerator generator = new InstrumentAnalysisReportGenerator(
                market, indicators, sources, objectMapper);

        var generated = generator.generate(context(32L, input));

        assertThat(generated.metrics()).containsEntry("close", "50000.01")
                .containsEntry("rsi14", "61.2").containsEntry("pointCount", 1);
        assertThat(generated.indicatorSnapshot()).isSameAs(snapshot);
        assertThat(generated.contentMarkdown()).contains("最新收盘价：50000.01", "RSI14：61.2")
                .doesNotContain("<script", "<div");
        assertThat(generated.evidence()).singleElement().satisfies(evidence -> {
            assertThat(evidence.payloadHash()).isEqualTo(snapshot.inputHash());
            assertThat(evidence.dataStartTime()).isEqualTo(snapshot.dataStartTime());
            assertThat(evidence.dataEndTime()).isEqualTo(snapshot.dataEndTime());
            assertThat(evidence.dataAsOf()).isEqualTo(NOW);
        });
        verify(market).instrument(42L);
        verify(indicators).calculate(42L, PriceType.MARK, BarInterval.H1,
                input.fromInclusive(), input.toExclusive(), NOW);
    }

    @Test
    void registryDiscoversStrategiesAndReportsMissingLaterPhaseCapability() {
        InvestmentMarketQueryService market = mock(InvestmentMarketQueryService.class);
        ResearchEvidenceSourceService sources = mock(ResearchEvidenceSourceService.class);
        MarketOverviewReportGenerator generator = new MarketOverviewReportGenerator(market, sources, objectMapper);
        InvestmentResearchReportGeneratorRegistry registry =
                new InvestmentResearchReportGeneratorRegistry(List.of(generator));

        assertThat(registry.require(InvestmentReportType.MARKET_OVERVIEW)).isSameAs(generator);
        assertThatThrownBy(() -> registry.require(InvestmentReportType.AGENT_ANALYSIS))
                .isInstanceOf(InvestmentException.class)
                .satisfies(error -> assertThat(((InvestmentException) error).getErrorCode())
                        .isEqualTo(InvestmentErrorCode.CAPABILITY_UNAVAILABLE));
    }

    private ResearchReportGenerationContext context(long reportId, FrozenResearchReportInput input) {
        return new ResearchReportGenerationContext(
                reportId, 11L, 1L, input, ResearchHashSupport.sha256(input.canonicalValue()));
    }

    private InvestmentInstrumentDetailResponse instrument() {
        return new InvestmentInstrumentDetailResponse(
                42L, "BITGET", "BTCUSDT", "BTC", "USDT", "USDT", "USDT",
                "USDT_PERPETUAL", "LINEAR", "ACTIVE", NOW.minusSeconds(100_000),
                List.of("MARK_CANDLE"), List.of("MARK"), List.of("H1"), NOW,
                null, false, null);
    }
}
