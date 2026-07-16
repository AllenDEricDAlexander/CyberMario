package top.egon.mario.investment.quant.dataset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.marketdata.repository.InvestmentContractSpecRepository;
import top.egon.mario.investment.marketdata.repository.InvestmentPositionTierRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.FundingRateJdbcRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.MarketBarJdbcRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketBarIntradayRow;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvestmentAsOfMarketDataTests {

    private static final Instant START = Instant.parse("2035-01-01T00:00:00Z");
    private static final Instant REVISION_AT = START.plusSeconds(2_000);

    private MarketBarJdbcRepository bars;
    private FundingRateJdbcRepository funding;
    private InvestmentContractSpecRepository specs;
    private InvestmentPositionTierRepository tiers;
    private MarketDataAsOfReader reader;

    @BeforeEach
    void setUp() {
        bars = mock(MarketBarJdbcRepository.class);
        funding = mock(FundingRateJdbcRepository.class);
        specs = mock(InvestmentContractSpecRepository.class);
        tiers = mock(InvestmentPositionTierRepository.class);
        reader = new MarketDataAsOfReader(bars, funding, specs, tiers);
        when(specs.findById(11L)).thenReturn(Optional.empty());
        when(tiers.findFirstBySourceIdAndInstrumentIdAndObservedAtLessThanEqualOrderByObservedAtDesc(
                anyLong(), anyLong(), any())).thenReturn(Optional.empty());
    }

    @Test
    void preservesTheRevisionSelectedByEachHalfOpenAsOfView() {
        when(bars.findIntradayAsOf(eq(3L), eq(11L), eq(PriceType.MARK), eq(BarInterval.M1),
                any(), any(), any(), anyInt(), anyInt())).thenAnswer(invocation -> {
            Instant asOf = invocation.getArgument(6);
            return List.of(row(asOf.isBefore(REVISION_AT) ? 1L : 2L,
                    asOf.isBefore(REVISION_AT) ? "v1" : "v2"));
        });
        var before = reader.read(request(REVISION_AT.minusNanos(1)));
        var after = reader.read(request(REVISION_AT));

        assertThat(before.instruments().getFirst().barSeries().getFirst().intradayRows())
                .singleElement().extracting(MarketBarIntradayRow::revision).isEqualTo(1L);
        assertThat(after.instruments().getFirst().barSeries().getFirst().intradayRows())
                .singleElement().extracting(MarketBarIntradayRow::revision).isEqualTo(2L);
        assertThat(before.instruments().getFirst().barSeries().getFirst().intradayRows().getFirst().checksum())
                .isEqualTo("v1");
    }

    @Test
    void advancesBoundedPagesWithoutHoldingAnEncompassingReaderTransaction() {
        List<MarketBarIntradayRow> firstPage = IntStream.range(0, MarketDataAsOfReader.PAGE_SIZE)
                .mapToObj(index -> rowAt(1L, "row-" + index, START.plusSeconds(index * 60L)))
                .toList();
        MarketBarIntradayRow finalRow = rowAt(1L, "last",
                START.plusSeconds(MarketDataAsOfReader.PAGE_SIZE * 60L));
        when(bars.findIntradayAsOf(eq(3L), eq(11L), eq(PriceType.MARK), eq(BarInterval.M1),
                any(), any(), any(), eq(0), eq(MarketDataAsOfReader.PAGE_SIZE))).thenReturn(firstPage);
        when(bars.findIntradayAsOf(eq(3L), eq(11L), eq(PriceType.MARK), eq(BarInterval.M1),
                any(), any(), any(), eq(MarketDataAsOfReader.PAGE_SIZE), eq(MarketDataAsOfReader.PAGE_SIZE)))
                .thenReturn(List.of(finalRow));

        var result = reader.read(new MarketDataAsOfReader.ReadRequest(
                3L, Set.of(11L), Set.of(PriceType.MARK), Set.of(BarInterval.M1), START,
                START.plusSeconds((MarketDataAsOfReader.PAGE_SIZE + 1L) * 60L),
                REVISION_AT.plusSeconds(100_000), false));

        assertThat(result.instruments().getFirst().barSeries().getFirst().intradayRows())
                .hasSize(MarketDataAsOfReader.PAGE_SIZE + 1)
                .last().extracting(MarketBarIntradayRow::checksum).isEqualTo("last");
        verify(bars).findIntradayAsOf(3L, 11L, PriceType.MARK, BarInterval.M1,
                START, START.plusSeconds((MarketDataAsOfReader.PAGE_SIZE + 1L) * 60L),
                REVISION_AT.plusSeconds(100_000), MarketDataAsOfReader.PAGE_SIZE,
                MarketDataAsOfReader.PAGE_SIZE);
    }

    private MarketDataAsOfReader.ReadRequest request(Instant asOf) {
        return new MarketDataAsOfReader.ReadRequest(3L, Set.of(11L), Set.of(PriceType.MARK),
                Set.of(BarInterval.M1), START, START.plusSeconds(60), asOf, false);
    }

    private MarketBarIntradayRow row(long revision, String checksum) {
        return rowAt(revision, checksum, START);
    }

    private MarketBarIntradayRow rowAt(long revision, String checksum, Instant openTime) {
        return new MarketBarIntradayRow(3L, 11L, PriceType.MARK, BarInterval.M1,
                openTime, openTime.plusSeconds(60), decimal("100"), decimal("110"), decimal("90"),
                decimal("105"), decimal("1"), decimal("105"), true, openTime.plusSeconds(60),
                openTime.plusSeconds(61), revision, START.minusSeconds(1),
                revision == 1 ? REVISION_AT : null, checksum);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
