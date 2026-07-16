package top.egon.mario.investment.quant.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.egon.mario.investment.common.access.InvestmentAccessService;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.marketdata.po.InvestmentContractSpecPo;
import top.egon.mario.investment.marketdata.po.InvestmentPositionTierPo;
import top.egon.mario.investment.marketdata.repository.jdbc.model.FundingRateRow;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketBarIntradayRow;
import top.egon.mario.investment.quant.backtest.BacktestDatasetLoader;
import top.egon.mario.investment.quant.dataset.DatasetCapabilityValidator;
import top.egon.mario.investment.quant.dataset.DatasetSnapshotPersistenceService;
import top.egon.mario.investment.quant.dataset.InvestmentDatasetHasher;
import top.egon.mario.investment.quant.dataset.InvestmentDatasetSnapshotService;
import top.egon.mario.investment.quant.dataset.MarketDataAsOfReader;
import top.egon.mario.investment.quant.po.InvestmentDatasetSnapshotItemPo;
import top.egon.mario.investment.quant.repository.InvestmentDatasetSnapshotItemRepository;
import top.egon.mario.investment.quant.repository.InvestmentDatasetSnapshotRepository;
import top.egon.mario.investment.quant.strategy.fixture.TestEmaCrossStrategy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BacktestDatasetLoaderTests {

    private static final Instant START = Instant.parse("2035-01-01T00:00:00Z");
    private static final Instant END = START.plusSeconds(120);
    private static final Instant AS_OF = END.plusSeconds(30);

    private MarketDataAsOfReader reader;
    private InvestmentDatasetSnapshotRepository snapshotRepository;
    private InvestmentDatasetSnapshotItemRepository itemRepository;
    private InvestmentDatasetHasher hasher;
    private BacktestDatasetLoader loader;

    @BeforeEach
    void setUp() {
        reader = mock(MarketDataAsOfReader.class);
        snapshotRepository = mock(InvestmentDatasetSnapshotRepository.class);
        itemRepository = mock(InvestmentDatasetSnapshotItemRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        hasher = new InvestmentDatasetHasher(objectMapper);
        loader = new BacktestDatasetLoader(snapshotRepository, itemRepository, reader, objectMapper, hasher);
    }

    @Test
    void replaysFrozenContractFeesAndTiersWhenCurrentMetadataHasChanged() {
        InvestmentContractSpecPo specification = specification();
        InvestmentPositionTierPo tier = tier();
        var frozen = createSnapshot(dataset(specification, tier, "MARKET-1", "MARK-1"));
        specification.setPriceEndStep(decimal("9"));
        specification.setMakerFeeRate(decimal("0.9"));
        specification.setTakerFeeRate(decimal("0.9"));
        specification.setMaxLeverage(decimal("1"));
        tier.setMaxLeverage(decimal("1"));
        when(reader.read(any())).thenReturn(dataset(specification, tier, "MARKET-1", "MARK-1"));

        var input = loader.load(frozen.snapshot().getId(), new TestEmaCrossStrategy(), decimal("10000"));

        var instrument = input.instruments().getFirst();
        assertThat(instrument.contractTerms().priceStep()).isEqualByComparingTo("0.1");
        assertThat(instrument.makerFeeRate()).isEqualByComparingTo("0.0002");
        assertThat(instrument.takerFeeRate()).isEqualByComparingTo("0.0006");
        assertThat(instrument.positionTiers().getFirst().maximumLeverage()).isEqualByComparingTo("50");
    }

    @Test
    void refusesReplayWhenAnAsOfMarketRowNoLongerMatchesTheManifestHash() {
        InvestmentContractSpecPo specification = specification();
        InvestmentPositionTierPo tier = tier();
        var frozen = createSnapshot(dataset(specification, tier, "MARKET-1", "MARK-1"));
        when(reader.read(any())).thenReturn(dataset(specification, tier, "tampered", "MARK-1"));

        assertThatThrownBy(() -> loader.load(frozen.snapshot().getId(),
                new TestEmaCrossStrategy(), decimal("10000")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BAR_INTRADAY hash mismatch");
    }

    @Test
    void refusesAQualityStatusThatMarksTheSnapshotNonReproducible() {
        var frozen = createSnapshot(dataset(specification(), tier(), "MARKET-1", "MARK-1"));
        frozen.snapshot().setQualityStatus("NOT_REPRODUCIBLE");
        clearInvocations(reader);

        assertThatThrownBy(() -> loader.load(frozen.snapshot().getId(),
                new TestEmaCrossStrategy(), decimal("10000")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not reproducible");
        verify(reader, never()).read(any());
    }

    private InvestmentDatasetSnapshotService.DatasetSnapshot createSnapshot(
            MarketDataAsOfReader.AsOfDataset dataset) {
        DatasetSnapshotPersistenceService persistence = mock(DatasetSnapshotPersistenceService.class);
        when(reader.read(any())).thenReturn(dataset);
        when(snapshotRepository.findByWorkspaceIdAndDatasetHash(anyLong(), anyString()))
                .thenReturn(Optional.empty());
        when(persistence.persist(any(), any())).thenAnswer(invocation -> {
            var snapshot = (top.egon.mario.investment.quant.po.InvestmentDatasetSnapshotPo)
                    invocation.getArgument(0);
            @SuppressWarnings("unchecked")
            List<InvestmentDatasetSnapshotItemPo> items = invocation.getArgument(1);
            snapshot.setId(91L);
            items.forEach(item -> item.setSnapshotId(91L));
            return new InvestmentDatasetSnapshotService.DatasetSnapshot(snapshot, items);
        });
        InvestmentDatasetSnapshotService snapshotService = new InvestmentDatasetSnapshotService(
                mock(InvestmentAccessService.class), reader, new DatasetCapabilityValidator(), hasher,
                snapshotRepository, itemRepository, persistence,
                Clock.fixed(AS_OF.plusSeconds(1), ZoneOffset.UTC));
        var frozen = snapshotService.create(new InvestmentDatasetSnapshotService.CreateCommand(
                5L, 7L, 3L, Set.of(11L), START, END, AS_OF, TestEmaCrossStrategy.DESCRIPTOR));
        when(snapshotRepository.findById(91L)).thenReturn(Optional.of(frozen.snapshot()));
        when(itemRepository.findBySnapshotIdOrderByInstrumentIdAscDataTypeAscPriceTypeAscIntervalCodeAsc(91L))
                .thenReturn(frozen.items());
        return frozen;
    }

    private MarketDataAsOfReader.AsOfDataset dataset(InvestmentContractSpecPo specification,
                                                      InvestmentPositionTierPo tier,
                                                      String marketChecksum, String markChecksum) {
        return new MarketDataAsOfReader.AsOfDataset(List.of(new MarketDataAsOfReader.AsOfInstrument(
                11L, specification, List.of(tier), List.of(
                new MarketDataAsOfReader.AsOfBarSeries(PriceType.MARKET, BarInterval.M1,
                        bars(PriceType.MARKET, marketChecksum), List.of()),
                new MarketDataAsOfReader.AsOfBarSeries(PriceType.MARK, BarInterval.M1,
                        bars(PriceType.MARK, markChecksum), List.of())), List.of(funding()))));
    }

    private List<MarketBarIntradayRow> bars(PriceType priceType, String checksumPrefix) {
        return List.of(bar(priceType, START, checksumPrefix + "-1"),
                bar(priceType, START.plusSeconds(60), checksumPrefix + "-2"));
    }

    private MarketBarIntradayRow bar(PriceType priceType, Instant open, String checksum) {
        return new MarketBarIntradayRow(3L, 11L, priceType, BarInterval.M1, open, open.plusSeconds(60),
                decimal("100"), decimal("110"), decimal("90"), decimal("105"), decimal("2"),
                decimal("210"), true, open.plusSeconds(60), open.plusSeconds(61), 1L,
                START.minusSeconds(1), null, checksum);
    }

    private FundingRateRow funding() {
        return new FundingRateRow(3L, 11L, START.plusSeconds(60), decimal("0.0001"),
                START.plusSeconds(61), 1L, START.minusSeconds(1), null, "funding-1");
    }

    private InvestmentContractSpecPo specification() {
        InvestmentContractSpecPo value = new InvestmentContractSpecPo();
        value.setInstrumentId(11L);
        value.setSourceId(3L);
        value.setPricePrecision(1);
        value.setQuantityPrecision(3);
        value.setPriceEndStep(decimal("0.10"));
        value.setQuantityStep(decimal("0.001"));
        value.setContractMultiplier(decimal("1"));
        value.setMinTradeQuantity(decimal("0.001"));
        value.setMinTradeNotional(decimal("5"));
        value.setMaxMarketOrderQuantity(decimal("100"));
        value.setMaxLimitOrderQuantity(decimal("200"));
        value.setMakerFeeRate(decimal("0.0002"));
        value.setTakerFeeRate(decimal("0.0006"));
        value.setMinLeverage(decimal("1"));
        value.setMaxLeverage(decimal("50"));
        value.setFundingIntervalHours(8);
        value.setBuyLimitPriceRatio(decimal("1.1"));
        value.setSellLimitPriceRatio(decimal("0.9"));
        value.setSourceUpdatedAt(START.minusSeconds(10));
        value.setIngestedAt(START.minusSeconds(9));
        value.setRevision(2L);
        value.setRawMetadataJson("{\"z\":1,\"a\":2}");
        return value;
    }

    private InvestmentPositionTierPo tier() {
        InvestmentPositionTierPo value = new InvestmentPositionTierPo();
        value.setSourceId(3L);
        value.setInstrumentId(11L);
        value.setObservedAt(START.minusSeconds(20));
        value.setTierLevel(1);
        value.setStartNotional(decimal("0"));
        value.setEndNotional(decimal("100000"));
        value.setMaxLeverage(decimal("50"));
        value.setMaintenanceMarginRate(decimal("0.005"));
        value.setSourceHash("tier-source");
        value.setIngestedAt(START.minusSeconds(19));
        value.setLastSeenAt(START.minusSeconds(18));
        return value;
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
