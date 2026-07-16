package top.egon.mario.investment.quant.dataset;

import org.springframework.stereotype.Service;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.access.InvestmentAccessService;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.marketdata.po.InvestmentContractSpecPo;
import top.egon.mario.investment.marketdata.po.InvestmentPositionTierPo;
import top.egon.mario.investment.marketdata.repository.jdbc.model.FundingRateRow;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketBarDailyRow;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketBarIntradayRow;
import top.egon.mario.investment.quant.po.InvestmentDatasetSnapshotItemPo;
import top.egon.mario.investment.quant.po.InvestmentDatasetSnapshotPo;
import top.egon.mario.investment.quant.repository.InvestmentDatasetSnapshotItemRepository;
import top.egon.mario.investment.quant.repository.InvestmentDatasetSnapshotRepository;
import top.egon.mario.investment.quant.strategy.StrategyDescriptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Materializes a code-strategy market-data dependency as an immutable, hash-addressed manifest.
 */
@Service
public class InvestmentDatasetSnapshotService {

    private final InvestmentAccessService accessService;
    private final MarketDataAsOfReader reader;
    private final DatasetCapabilityValidator validator;
    private final InvestmentDatasetHasher hasher;
    private final InvestmentDatasetSnapshotRepository snapshotRepository;
    private final InvestmentDatasetSnapshotItemRepository itemRepository;
    private final DatasetSnapshotPersistenceService persistenceService;
    private final Clock clock;

    public InvestmentDatasetSnapshotService(InvestmentAccessService accessService,
                                            MarketDataAsOfReader reader,
                                            DatasetCapabilityValidator validator,
                                            InvestmentDatasetHasher hasher,
                                            InvestmentDatasetSnapshotRepository snapshotRepository,
                                            InvestmentDatasetSnapshotItemRepository itemRepository,
                                            DatasetSnapshotPersistenceService persistenceService,
                                            Clock clock) {
        this.accessService = accessService;
        this.reader = reader;
        this.validator = validator;
        this.hasher = hasher;
        this.snapshotRepository = snapshotRepository;
        this.itemRepository = itemRepository;
        this.persistenceService = persistenceService;
        this.clock = clock;
    }

    /**
     * Reads outside a long transaction, validates the complete view, then persists it in one bounded transaction.
     */
    public DatasetSnapshot create(CreateCommand command) {
        Objects.requireNonNull(command, "command");
        accessService.requireWorkspaceOwner(command.workspaceId(), command.actorId());
        Set<PriceType> priceTypes = requiredPriceTypes(command.strategy().requiredCapabilities());
        Set<BarInterval> intervals = Set.of(command.strategy().evaluationInterval());
        MarketDataAsOfReader.ReadRequest request = new MarketDataAsOfReader.ReadRequest(
                command.sourceId(), command.instrumentIds(), priceTypes, intervals,
                command.startTime(), command.endTime(), command.dataAsOf(),
                command.strategy().requiredCapabilities().contains(DataCapability.FUNDING_RATE));
        MarketDataAsOfReader.AsOfDataset dataset = reader.read(request);
        validator.validate(request, dataset, command.strategy().requiredCapabilities());
        SnapshotMaterial material = materialize(command, request, dataset);
        return snapshotRepository.findByWorkspaceIdAndDatasetHash(command.workspaceId(), material.datasetHash())
                .map(snapshot -> new DatasetSnapshot(snapshot,
                        itemRepository.findBySnapshotIdOrderByInstrumentIdAscDataTypeAscPriceTypeAscIntervalCodeAsc(
                                snapshot.getId())))
                .orElseGet(() -> persistenceService.persist(material.snapshot(), material.items()));
    }

    private SnapshotMaterial materialize(CreateCommand command, MarketDataAsOfReader.ReadRequest request,
                                         MarketDataAsOfReader.AsOfDataset dataset) {
        Instant createdAt = clock.instant();
        List<Map<String, Object>> specificationValues = dataset.instruments().stream()
                .map(this::contractSpecValue).toList();
        List<Map<String, Object>> tierValues = dataset.instruments().stream()
                .flatMap(instrument -> instrument.positionTiers().stream()
                        .map(tier -> tierValue(instrument.instrumentId(), tier)))
                .toList();
        List<Map<String, Object>> fundingValues = dataset.instruments().stream()
                .flatMap(instrument -> instrument.fundingRates().stream().map(this::fundingValue))
                .toList();
        String contractSpecHash = hasher.hash(specificationValues);
        String positionTierHash = hasher.hash(tierValues);
        String fundingDataHash = hasher.hash(fundingValues);
        String feeJson = hasher.canonicalJson(feeSnapshot(command.strategy(), dataset));
        String slippageJson = hasher.canonicalJson(Map.of("modelCode", command.strategy().slippageModelCode()));
        List<InvestmentDatasetSnapshotItemPo> items = itemValues(dataset, createdAt);
        List<Map<String, Object>> itemManifest = items.stream().map(this::itemManifest).toList();
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("sourceId", command.sourceId());
        manifest.put("instrumentIds", command.instrumentIds().stream().sorted().toList());
        manifest.put("startTime", command.startTime().toString());
        manifest.put("endTime", command.endTime().toString());
        manifest.put("dataAsOf", command.dataAsOf().toString());
        manifest.put("strategyCode", command.strategy().strategyCode());
        manifest.put("strategyVersion", command.strategy().strategyVersion());
        manifest.put("contractSpecHash", contractSpecHash);
        manifest.put("positionTierHash", positionTierHash);
        manifest.put("fundingDataHash", fundingDataHash);
        manifest.put("feeModel", feeJson);
        manifest.put("slippageModel", slippageJson);
        manifest.put("matchingModelCode", command.strategy().matchingModelCode());
        manifest.put("items", itemManifest);
        String datasetHash = hasher.hash(manifest);

        InvestmentDatasetSnapshotPo snapshot = new InvestmentDatasetSnapshotPo();
        snapshot.setWorkspaceId(command.workspaceId());
        snapshot.setSourceId(command.sourceId());
        snapshot.setStartTime(command.startTime());
        snapshot.setEndTime(command.endTime());
        snapshot.setDataAsOf(command.dataAsOf());
        snapshot.setIntervalsJson(hasher.canonicalJson(request.intervals().stream().map(Enum::name).sorted().toList()));
        snapshot.setPriceTypesJson(hasher.canonicalJson(
                request.priceTypes().stream().map(Enum::name).sorted().toList()));
        snapshot.setContractSpecHash(contractSpecHash);
        snapshot.setPositionTierHash(positionTierHash);
        snapshot.setFundingDataHash(fundingDataHash);
        snapshot.setContractSpecSnapshotJson(hasher.canonicalJson(specificationValues));
        snapshot.setPositionTierSnapshotJson(hasher.canonicalJson(tierValues));
        snapshot.setFeeModelSnapshotJson(feeJson);
        snapshot.setSlippageModelSnapshotJson(slippageJson);
        snapshot.setDatasetHash(datasetHash);
        snapshot.setQualityStatus("PENDING");
        snapshot.setCreatedAt(createdAt);
        snapshot.setCreatedBy(command.actorId());
        return new SnapshotMaterial(snapshot, items, datasetHash);
    }

    private List<InvestmentDatasetSnapshotItemPo> itemValues(
            MarketDataAsOfReader.AsOfDataset dataset, Instant createdAt) {
        List<InvestmentDatasetSnapshotItemPo> result = new ArrayList<>();
        for (MarketDataAsOfReader.AsOfInstrument instrument : dataset.instruments()) {
            if (instrument.contractSpec() != null) {
                Instant time = instrument.contractSpec().getIngestedAt();
                result.add(item(instrument.instrumentId(), "CONTRACT_SPEC", PriceType.NONE, BarInterval.NONE,
                        time, time, 1, instrument.contractSpec().getRevision(),
                        hasher.hash(contractSpecValue(instrument)), createdAt));
            }
            if (!instrument.positionTiers().isEmpty()) {
                Instant time = instrument.positionTiers().getFirst().getObservedAt();
                List<Map<String, Object>> values = instrument.positionTiers().stream()
                        .map(tier -> tierValue(instrument.instrumentId(), tier)).toList();
                result.add(item(instrument.instrumentId(), "POSITION_TIER", PriceType.NONE, BarInterval.NONE,
                        time, time, values.size(), 0, hasher.hash(values), createdAt));
            }
            for (MarketDataAsOfReader.AsOfBarSeries series : instrument.barSeries()) {
                if (series.interval() == BarInterval.D1) {
                    List<MarketBarDailyRow> rows = series.dailyRows();
                    result.add(item(instrument.instrumentId(), "BAR_DAILY", series.priceType(), series.interval(),
                            rows.getFirst().barDate().atStartOfDay().toInstant(ZoneOffset.UTC),
                            rows.getLast().barDate().atStartOfDay().toInstant(ZoneOffset.UTC), rows.size(),
                            rows.stream().mapToLong(MarketBarDailyRow::revision).max().orElseThrow(),
                            hasher.hash(rows.stream().map(this::dailyValue).toList()), createdAt));
                } else {
                    List<MarketBarIntradayRow> rows = series.intradayRows();
                    result.add(item(instrument.instrumentId(), "BAR_INTRADAY", series.priceType(), series.interval(),
                            rows.getFirst().openTime(), rows.getLast().openTime(), rows.size(),
                            rows.stream().mapToLong(MarketBarIntradayRow::revision).max().orElseThrow(),
                            hasher.hash(rows.stream().map(this::intradayValue).toList()), createdAt));
                }
            }
            if (!instrument.fundingRates().isEmpty()) {
                List<FundingRateRow> rows = instrument.fundingRates();
                result.add(item(instrument.instrumentId(), "FUNDING_RATE", PriceType.NONE, BarInterval.NONE,
                        rows.getFirst().fundingTime(), rows.getLast().fundingTime(), rows.size(),
                        rows.stream().mapToLong(FundingRateRow::revision).max().orElseThrow(),
                        hasher.hash(rows.stream().map(this::fundingValue).toList()), createdAt));
            }
        }
        return result;
    }

    private InvestmentDatasetSnapshotItemPo item(long instrumentId, String dataType,
                                                  PriceType priceType, BarInterval interval,
                                                  Instant firstTime, Instant lastTime, long rowCount,
                                                  long maxRevision, String dataHash, Instant createdAt) {
        InvestmentDatasetSnapshotItemPo item = new InvestmentDatasetSnapshotItemPo();
        item.setInstrumentId(instrumentId);
        item.setDataType(dataType);
        item.setPriceType(priceType.name());
        item.setIntervalCode(interval.name());
        item.setFirstTime(firstTime);
        item.setLastTime(lastTime);
        item.setRowCount(rowCount);
        item.setMaxRevision(maxRevision);
        item.setDataHash(dataHash);
        item.setCreatedAt(createdAt);
        return item;
    }

    private Map<String, Object> contractSpecValue(MarketDataAsOfReader.AsOfInstrument instrument) {
        InvestmentContractSpecPo value = instrument.contractSpec();
        if (value == null) {
            return Map.of("instrumentId", instrument.instrumentId());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instrumentId", instrument.instrumentId());
        result.put("sourceId", value.getSourceId());
        result.put("pricePrecision", value.getPricePrecision());
        result.put("quantityPrecision", value.getQuantityPrecision());
        result.put("priceEndStep", decimal(value.getPriceEndStep()));
        result.put("quantityStep", decimal(value.getQuantityStep()));
        result.put("contractMultiplier", decimal(value.getContractMultiplier()));
        result.put("minTradeQuantity", decimal(value.getMinTradeQuantity()));
        result.put("minTradeNotional", decimal(value.getMinTradeNotional()));
        result.put("maxMarketOrderQuantity", decimal(value.getMaxMarketOrderQuantity()));
        result.put("maxLimitOrderQuantity", decimal(value.getMaxLimitOrderQuantity()));
        result.put("makerFeeRate", decimal(value.getMakerFeeRate()));
        result.put("takerFeeRate", decimal(value.getTakerFeeRate()));
        result.put("minLeverage", decimal(value.getMinLeverage()));
        result.put("maxLeverage", decimal(value.getMaxLeverage()));
        result.put("fundingIntervalHours", value.getFundingIntervalHours());
        result.put("buyLimitPriceRatio", decimal(value.getBuyLimitPriceRatio()));
        result.put("sellLimitPriceRatio", decimal(value.getSellLimitPriceRatio()));
        result.put("sourceUpdatedAt", instant(value.getSourceUpdatedAt()));
        result.put("ingestedAt", value.getIngestedAt().toString());
        result.put("revision", value.getRevision());
        result.put("rawMetadata", hasher.canonicalizeJson(value.getRawMetadataJson()));
        return result;
    }

    private Map<String, Object> tierValue(long instrumentId, InvestmentPositionTierPo value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instrumentId", instrumentId);
        result.put("sourceId", value.getSourceId());
        result.put("observedAt", value.getObservedAt().toString());
        result.put("tierLevel", value.getTierLevel());
        result.put("startNotional", decimal(value.getStartNotional()));
        result.put("endNotional", decimal(value.getEndNotional()));
        result.put("maxLeverage", decimal(value.getMaxLeverage()));
        result.put("maintenanceMarginRate", decimal(value.getMaintenanceMarginRate()));
        result.put("sourceHash", value.getSourceHash());
        return result;
    }

    private Map<String, Object> intradayValue(MarketBarIntradayRow value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("openTime", value.openTime().toString());
        result.put("closeTime", value.closeTime().toString());
        barPrices(result, value.openPrice(), value.highPrice(), value.lowPrice(), value.closePrice(),
                value.baseVolume(), value.quoteVolume());
        result.put("closed", value.closed());
        result.put("revision", value.revision());
        result.put("checksum", value.checksum());
        return result;
    }

    private Map<String, Object> dailyValue(MarketBarDailyRow value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("barDate", value.barDate().toString());
        barPrices(result, value.openPrice(), value.highPrice(), value.lowPrice(), value.closePrice(),
                value.baseVolume(), value.quoteVolume());
        result.put("closed", value.closed());
        result.put("revision", value.revision());
        result.put("checksum", value.checksum());
        return result;
    }

    private Map<String, Object> fundingValue(FundingRateRow value) {
        return Map.of("instrumentId", value.instrumentId(), "fundingTime", value.fundingTime().toString(),
                "fundingRate", decimal(value.fundingRate()), "revision", value.revision(),
                "checksum", value.checksum());
    }

    private static void barPrices(Map<String, Object> result, BigDecimal open, BigDecimal high,
                                  BigDecimal low, BigDecimal close, BigDecimal base, BigDecimal quote) {
        result.put("open", decimal(open));
        result.put("high", decimal(high));
        result.put("low", decimal(low));
        result.put("close", decimal(close));
        result.put("baseVolume", decimal(base));
        result.put("quoteVolume", decimal(quote));
    }

    private static Map<String, Object> feeSnapshot(StrategyDescriptor descriptor,
                                                    MarketDataAsOfReader.AsOfDataset dataset) {
        List<Map<String, Object>> instruments = dataset.instruments().stream()
                .filter(value -> value.contractSpec() != null)
                .map(value -> Map.<String, Object>of(
                        "instrumentId", value.instrumentId(),
                        "makerFeeRate", decimal(value.contractSpec().getMakerFeeRate()),
                        "takerFeeRate", decimal(value.contractSpec().getTakerFeeRate())))
                .toList();
        return Map.of("modelCode", descriptor.feeModelCode(), "instruments", instruments);
    }

    private Map<String, Object> itemManifest(InvestmentDatasetSnapshotItemPo item) {
        return Map.of("instrumentId", item.getInstrumentId(), "dataType", item.getDataType(),
                "priceType", item.getPriceType(), "interval", item.getIntervalCode(),
                "firstTime", item.getFirstTime().toString(), "lastTime", item.getLastTime().toString(),
                "rowCount", item.getRowCount(), "maxRevision", item.getMaxRevision(),
                "dataHash", item.getDataHash());
    }

    private static Set<PriceType> requiredPriceTypes(Set<DataCapability> capabilities) {
        EnumSet<PriceType> result = EnumSet.noneOf(PriceType.class);
        if (capabilities.contains(DataCapability.MARKET_CANDLE)) {
            result.add(PriceType.MARKET);
        }
        if (capabilities.contains(DataCapability.MARK_CANDLE)) {
            result.add(PriceType.MARK);
        }
        if (capabilities.contains(DataCapability.INDEX_CANDLE)) {
            result.add(PriceType.INDEX);
        }
        if (result.isEmpty()) {
            throw new InvestmentException(InvestmentErrorCode.CAPABILITY_UNAVAILABLE,
                    "Strategy requires no historical candle capability");
        }
        return Set.copyOf(result);
    }

    private static String decimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String instant(Instant value) {
        return value == null ? null : value.toString();
    }

    public record CreateCommand(long actorId, long workspaceId, long sourceId, Set<Long> instrumentIds,
                                Instant startTime, Instant endTime, Instant dataAsOf,
                                StrategyDescriptor strategy) {
        public CreateCommand {
            if (actorId <= 0 || workspaceId <= 0 || sourceId <= 0) {
                throw new IllegalArgumentException("Actor, workspace and source ids must be positive");
            }
            if (instrumentIds == null || instrumentIds.isEmpty()) {
                throw new IllegalArgumentException("At least one instrument is required");
            }
            instrumentIds = Set.copyOf(instrumentIds);
            Objects.requireNonNull(strategy, "strategy");
        }
    }

    public record DatasetSnapshot(InvestmentDatasetSnapshotPo snapshot,
                                  List<InvestmentDatasetSnapshotItemPo> items) {
        public DatasetSnapshot {
            items = List.copyOf(items);
        }
    }

    private record SnapshotMaterial(InvestmentDatasetSnapshotPo snapshot,
                                    List<InvestmentDatasetSnapshotItemPo> items,
                                    String datasetHash) {
    }
}
