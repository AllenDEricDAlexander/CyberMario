package top.egon.mario.investment.quant.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.access.InvestmentAccessService;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.marketdata.po.InvestmentContractSpecPo;
import top.egon.mario.investment.marketdata.po.InvestmentPositionTierPo;
import top.egon.mario.investment.marketdata.repository.jdbc.model.FundingRateRow;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketBarIntradayRow;
import top.egon.mario.investment.quant.po.InvestmentDatasetSnapshotItemPo;
import top.egon.mario.investment.quant.po.InvestmentDatasetSnapshotPo;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvestmentDatasetSnapshotServiceTests {

    private static final Instant START = Instant.parse("2035-01-01T00:00:00Z");
    private static final Instant END = START.plusSeconds(120);
    private static final Instant AS_OF = END.plusSeconds(30);

    private InvestmentAccessService accessService;
    private MarketDataAsOfReader reader;
    private InvestmentDatasetSnapshotRepository snapshotRepository;
    private InvestmentDatasetSnapshotItemRepository itemRepository;
    private DatasetSnapshotPersistenceService persistenceService;
    private InvestmentDatasetSnapshotService service;

    @BeforeEach
    void setUp() {
        accessService = mock(InvestmentAccessService.class);
        reader = mock(MarketDataAsOfReader.class);
        snapshotRepository = mock(InvestmentDatasetSnapshotRepository.class);
        itemRepository = mock(InvestmentDatasetSnapshotItemRepository.class);
        persistenceService = mock(DatasetSnapshotPersistenceService.class);
        service = new InvestmentDatasetSnapshotService(accessService, reader, new DatasetCapabilityValidator(),
                new InvestmentDatasetHasher(new ObjectMapper()), snapshotRepository, itemRepository,
                persistenceService, Clock.fixed(AS_OF.plusSeconds(1), ZoneOffset.UTC));
    }

    @Test
    void copiesExactDependenciesAndPersistsOneImmutableManifest() {
        when(reader.read(any())).thenReturn(dataset());
        when(snapshotRepository.findByWorkspaceIdAndDatasetHash(anyLong(), anyString()))
                .thenReturn(Optional.empty());
        when(persistenceService.persist(any(), any())).thenAnswer(invocation -> {
            InvestmentDatasetSnapshotPo snapshot = invocation.getArgument(0);
            List<InvestmentDatasetSnapshotItemPo> items = invocation.getArgument(1);
            snapshot.setId(91L);
            return new InvestmentDatasetSnapshotService.DatasetSnapshot(snapshot, items);
        });

        var result = service.create(command());

        ArgumentCaptor<InvestmentDatasetSnapshotPo> snapshotCaptor =
                ArgumentCaptor.forClass(InvestmentDatasetSnapshotPo.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InvestmentDatasetSnapshotItemPo>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(persistenceService).persist(snapshotCaptor.capture(), itemsCaptor.capture());
        InvestmentDatasetSnapshotPo snapshot = snapshotCaptor.getValue();
        assertThat(result.snapshot().getId()).isEqualTo(91L);
        assertThat(snapshot.getDatasetHash()).hasSize(64);
        assertThat(snapshot.getContractSpecSnapshotJson()).contains("priceEndStep", "0.1", "rawMetadata");
        assertThat(snapshot.getPositionTierSnapshotJson()).contains("maintenanceMarginRate", "0.005");
        assertThat(snapshot.getFeeModelSnapshotJson()).contains("CONTRACT_RATE_V1", "makerFeeRate");
        assertThat(snapshot.getSlippageModelSnapshotJson()).contains("FIXED_BPS_5");
        assertThat(itemsCaptor.getValue()).extracting(InvestmentDatasetSnapshotItemPo::getDataType)
                .containsExactlyInAnyOrder("CONTRACT_SPEC", "POSITION_TIER", "BAR_INTRADAY",
                        "BAR_INTRADAY", "FUNDING_RATE");
        assertThat(itemsCaptor.getValue()).filteredOn(item -> item.getDataType().equals("BAR_INTRADAY"))
                .allSatisfy(item -> {
                    assertThat(item.getRowCount()).isEqualTo(2);
                    assertThat(item.getMaxRevision()).isEqualTo(1);
                    assertThat(item.getDataHash()).hasSize(64);
                });
        verify(accessService).requireWorkspaceOwner(7L, 5L);
    }

    @Test
    void returnsExistingSameHashSnapshotWithoutWritingAgain() {
        when(reader.read(any())).thenReturn(dataset());
        InvestmentDatasetSnapshotPo existing = new InvestmentDatasetSnapshotPo();
        existing.setId(81L);
        when(snapshotRepository.findByWorkspaceIdAndDatasetHash(anyLong(), anyString()))
                .thenReturn(Optional.of(existing));
        when(itemRepository.findBySnapshotIdOrderByInstrumentIdAscDataTypeAscPriceTypeAscIntervalCodeAsc(81L))
                .thenReturn(List.of());

        var result = service.create(command());

        assertThat(result.snapshot()).isSameAs(existing);
        verify(persistenceService, never()).persist(any(), any());
    }

    @Test
    void ownerFailureStopsBeforeAnyMarketDataRead() {
        doThrow(new InvestmentException(InvestmentErrorCode.FORBIDDEN, "denied"))
                .when(accessService).requireWorkspaceOwner(7L, 5L);

        assertThatThrownBy(() -> service.create(command()))
                .isInstanceOfSatisfying(InvestmentException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(InvestmentErrorCode.FORBIDDEN));
        verify(reader, never()).read(any());
    }

    @Test
    void rejectsOpenOrGappedBarsBeforePersistence() {
        MarketDataAsOfReader.AsOfDataset invalid = new MarketDataAsOfReader.AsOfDataset(List.of(
                new MarketDataAsOfReader.AsOfInstrument(11L, specification(), List.of(tier()), List.of(
                        new MarketDataAsOfReader.AsOfBarSeries(PriceType.MARKET, BarInterval.M1,
                                List.of(bar(PriceType.MARKET, START, true, "a"),
                                        bar(PriceType.MARKET, START.plusSeconds(60), false, "b")), List.of()),
                        new MarketDataAsOfReader.AsOfBarSeries(PriceType.MARK, BarInterval.M1,
                                bars(PriceType.MARK), List.of())), List.of(funding()))));
        when(reader.read(any())).thenReturn(invalid);

        assertThatThrownBy(() -> service.create(command()))
                .isInstanceOfSatisfying(InvestmentException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(InvestmentErrorCode.DATA_UNAVAILABLE));
        verify(persistenceService, never()).persist(any(), any());
    }

    private InvestmentDatasetSnapshotService.CreateCommand command() {
        return new InvestmentDatasetSnapshotService.CreateCommand(
                5L, 7L, 3L, Set.of(11L), START, END, AS_OF, TestEmaCrossStrategy.DESCRIPTOR);
    }

    private MarketDataAsOfReader.AsOfDataset dataset() {
        return new MarketDataAsOfReader.AsOfDataset(List.of(new MarketDataAsOfReader.AsOfInstrument(
                11L, specification(), List.of(tier()), List.of(
                new MarketDataAsOfReader.AsOfBarSeries(
                        PriceType.MARKET, BarInterval.M1, bars(PriceType.MARKET), List.of()),
                new MarketDataAsOfReader.AsOfBarSeries(
                        PriceType.MARK, BarInterval.M1, bars(PriceType.MARK), List.of())),
                List.of(funding()))));
    }

    private List<MarketBarIntradayRow> bars(PriceType priceType) {
        return List.of(bar(priceType, START, true, priceType + "-1"),
                bar(priceType, START.plusSeconds(60), true, priceType + "-2"));
    }

    private MarketBarIntradayRow bar(PriceType priceType, Instant open, boolean closed, String checksum) {
        return new MarketBarIntradayRow(3L, 11L, priceType, BarInterval.M1, open, open.plusSeconds(60),
                decimal("100"), decimal("110"), decimal("90"), decimal("105"), decimal("2"),
                decimal("210"), closed, open.plusSeconds(60), open.plusSeconds(61), 1L,
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
