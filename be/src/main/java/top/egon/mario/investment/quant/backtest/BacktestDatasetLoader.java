package top.egon.mario.investment.quant.backtest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.marketdata.repository.jdbc.model.FundingRateRow;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketBarDailyRow;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketBarIntradayRow;
import top.egon.mario.investment.portfolio.margin.PositionTier;
import top.egon.mario.investment.quant.backtest.model.BacktestInput;
import top.egon.mario.investment.quant.backtest.model.BacktestInstrumentInput;
import top.egon.mario.investment.quant.backtest.model.FundingPoint;
import top.egon.mario.investment.quant.dataset.InvestmentDatasetHasher;
import top.egon.mario.investment.quant.dataset.MarketDataAsOfReader;
import top.egon.mario.investment.quant.po.InvestmentDatasetSnapshotItemPo;
import top.egon.mario.investment.quant.po.InvestmentDatasetSnapshotPo;
import top.egon.mario.investment.quant.repository.InvestmentDatasetSnapshotItemRepository;
import top.egon.mario.investment.quant.repository.InvestmentDatasetSnapshotRepository;
import top.egon.mario.investment.quant.strategy.InvestmentStrategy;
import top.egon.mario.investment.quant.strategy.StrategyDescriptor;
import top.egon.mario.investment.trading.matching.model.ContractTerms;
import top.egon.mario.investment.trading.matching.model.FuturesBar;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reconstructs exact engine input from an immutable dataset manifest and its SCD2 cutoff.
 */
@Component
public class BacktestDatasetLoader {

    private static final Set<String> REPLAYABLE_QUALITY_STATUSES = Set.of("PENDING", "VERIFIED");
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final InvestmentDatasetSnapshotRepository snapshotRepository;
    private final InvestmentDatasetSnapshotItemRepository itemRepository;
    private final MarketDataAsOfReader reader;
    private final ObjectMapper objectMapper;
    private final InvestmentDatasetHasher hasher;

    public BacktestDatasetLoader(InvestmentDatasetSnapshotRepository snapshotRepository,
                                 InvestmentDatasetSnapshotItemRepository itemRepository,
                                 MarketDataAsOfReader reader,
                                 ObjectMapper objectMapper,
                                 InvestmentDatasetHasher hasher) {
        this.snapshotRepository = snapshotRepository;
        this.itemRepository = itemRepository;
        this.reader = reader;
        this.objectMapper = objectMapper;
        this.hasher = hasher;
    }

    public BacktestInput load(long snapshotId, InvestmentStrategy strategy, BigDecimal initialEquity) {
        InvestmentDatasetSnapshotPo snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new IllegalStateException("Backtest dataset snapshot not found"));
        if (!REPLAYABLE_QUALITY_STATUSES.contains(snapshot.getQualityStatus())) {
            throw new IllegalStateException("Backtest dataset snapshot is not reproducible: "
                    + snapshot.getQualityStatus());
        }
        List<InvestmentDatasetSnapshotItemPo> items = itemRepository
                .findBySnapshotIdOrderByInstrumentIdAscDataTypeAscPriceTypeAscIntervalCodeAsc(snapshotId);
        if (items.isEmpty()) {
            throw new IllegalStateException("Backtest dataset snapshot has no manifest items");
        }
        Set<Long> instrumentIds = items.stream().map(InvestmentDatasetSnapshotItemPo::getInstrumentId)
                .collect(Collectors.toUnmodifiableSet());
        EnumSet<PriceType> priceTypes = items.stream()
                .filter(item -> item.getDataType().startsWith("BAR_"))
                .map(item -> PriceType.valueOf(item.getPriceType()))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(PriceType.class)));
        EnumSet<BarInterval> intervals = items.stream()
                .filter(item -> item.getDataType().startsWith("BAR_"))
                .map(item -> BarInterval.valueOf(item.getIntervalCode()))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(BarInterval.class)));
        if (intervals.size() != 1 || !priceTypes.containsAll(Set.of(PriceType.MARKET, PriceType.MARK))) {
            throw new IllegalStateException("Backtest snapshot lacks one aligned MARKET/MARK bar interval");
        }
        boolean includeFunding = items.stream().anyMatch(item -> item.getDataType().equals("FUNDING_RATE"));
        MarketDataAsOfReader.AsOfDataset dataset = reader.read(new MarketDataAsOfReader.ReadRequest(
                snapshot.getSourceId(), instrumentIds, priceTypes, intervals,
                snapshot.getStartTime(), snapshot.getEndTime(), snapshot.getDataAsOf(), includeFunding));

        FrozenDataset frozen = frozenDataset(snapshot, items, strategy.descriptor());
        validateMarketData(items, dataset, snapshot.getFundingDataHash());
        Map<Long, MarketDataAsOfReader.AsOfInstrument> marketByInstrument = dataset.instruments().stream()
                .collect(Collectors.toUnmodifiableMap(MarketDataAsOfReader.AsOfInstrument::instrumentId,
                        Function.identity()));
        if (!marketByInstrument.keySet().equals(instrumentIds)) {
            throw new IllegalStateException("Backtest dataset instrument set no longer matches its manifest");
        }
        List<BacktestInstrumentInput> instruments = instrumentIds.stream().sorted()
                .map(instrumentId -> toInput(marketByInstrument.get(instrumentId), frozen, strategy.descriptor()))
                .toList();
        return new BacktestInput(strategy, initialEquity, instruments);
    }

    private FrozenDataset frozenDataset(InvestmentDatasetSnapshotPo snapshot,
                                        List<InvestmentDatasetSnapshotItemPo> items,
                                        StrategyDescriptor descriptor) {
        List<Map<String, Object>> specifications = readList(snapshot.getContractSpecSnapshotJson(),
                "contract specification snapshot");
        List<Map<String, Object>> tiers = readList(snapshot.getPositionTierSnapshotJson(),
                "position tier snapshot");
        Map<String, Object> feeModel = readMap(snapshot.getFeeModelSnapshotJson(), "fee model snapshot");
        Map<String, Object> slippageModel = readMap(snapshot.getSlippageModelSnapshotJson(),
                "slippage model snapshot");
        requireHash("contract specification", snapshot.getContractSpecHash(), hasher.hash(specifications));
        requireHash("position tier", snapshot.getPositionTierHash(), hasher.hash(tiers));
        if (!descriptor.feeModelCode().equals(text(feeModel, "modelCode"))) {
            throw new IllegalStateException("Backtest fee model no longer matches the strategy release");
        }
        if (!descriptor.slippageModelCode().equals(text(slippageModel, "modelCode"))) {
            throw new IllegalStateException("Backtest slippage model no longer matches the strategy release");
        }
        requireHash("dataset manifest", snapshot.getDatasetHash(), hasher.hash(datasetManifest(
                snapshot, items, descriptor)));

        Map<Long, Map<String, Object>> specificationsByInstrument = specifications.stream()
                .collect(Collectors.toUnmodifiableMap(value -> number(value, "instrumentId").longValue(),
                        Function.identity()));
        Map<Long, List<Map<String, Object>>> tiersByInstrument = tiers.stream()
                .collect(Collectors.groupingBy(value -> number(value, "instrumentId").longValue()));
        List<Map<String, Object>> feeInstruments = mapList(feeModel.get("instruments"),
                "fee model instruments");
        Map<Long, Map<String, Object>> feesByInstrument = feeInstruments.stream()
                .collect(Collectors.toUnmodifiableMap(value -> number(value, "instrumentId").longValue(),
                        Function.identity()));
        validateFrozenItems(items, specificationsByInstrument, tiersByInstrument);
        return new FrozenDataset(specificationsByInstrument, Map.copyOf(tiersByInstrument), feesByInstrument);
    }

    private void validateFrozenItems(List<InvestmentDatasetSnapshotItemPo> items,
                                     Map<Long, Map<String, Object>> specifications,
                                     Map<Long, List<Map<String, Object>>> tiers) {
        for (InvestmentDatasetSnapshotItemPo item : items) {
            if (item.getDataType().equals("CONTRACT_SPEC")) {
                Map<String, Object> value = required(specifications, item.getInstrumentId(),
                        "Frozen contract specification is missing");
                requireItem(item, 1, number(value, "revision").longValue(), hasher.hash(value));
            } else if (item.getDataType().equals("POSITION_TIER")) {
                List<Map<String, Object>> values = required(tiers, item.getInstrumentId(),
                        "Frozen position tiers are missing");
                requireItem(item, values.size(), 0, hasher.hash(values));
            }
        }
    }

    private void validateMarketData(List<InvestmentDatasetSnapshotItemPo> items,
                                    MarketDataAsOfReader.AsOfDataset dataset,
                                    String expectedFundingHash) {
        Map<Long, MarketDataAsOfReader.AsOfInstrument> instruments = dataset.instruments().stream()
                .collect(Collectors.toUnmodifiableMap(MarketDataAsOfReader.AsOfInstrument::instrumentId,
                        Function.identity()));
        List<FundingRateRow> allFunding = new ArrayList<>();
        for (InvestmentDatasetSnapshotItemPo item : items) {
            MarketDataAsOfReader.AsOfInstrument instrument = required(instruments, item.getInstrumentId(),
                    "Backtest market data instrument is missing");
            switch (item.getDataType()) {
                case "BAR_INTRADAY" -> validateIntraday(item, series(instrument, item).intradayRows());
                case "BAR_DAILY" -> validateDaily(item, series(instrument, item).dailyRows());
                case "FUNDING_RATE" -> {
                    List<FundingRateRow> rows = instrument.fundingRates();
                    requireItem(item, rows.size(), maxFundingRevision(rows), hasher.hashFundingRates(rows));
                    requireRange(item, rows.getFirst().fundingTime(), rows.getLast().fundingTime());
                }
                case "CONTRACT_SPEC", "POSITION_TIER" -> {
                    // These values are reconstructed from the frozen JSON above, never from current tables.
                }
                default -> throw new IllegalStateException("Unsupported backtest dataset item: "
                        + item.getDataType());
            }
        }
        dataset.instruments().stream().sorted(Comparator.comparingLong(MarketDataAsOfReader.AsOfInstrument::instrumentId))
                .forEach(instrument -> allFunding.addAll(instrument.fundingRates()));
        requireHash("funding data", expectedFundingHash, hasher.hash(hasher.fundingValues(allFunding)));
    }

    private void validateIntraday(InvestmentDatasetSnapshotItemPo item, List<MarketBarIntradayRow> rows) {
        if (rows.isEmpty()) {
            throw new IllegalStateException("Backtest intraday bars are missing");
        }
        long maxRevision = rows.stream().mapToLong(MarketBarIntradayRow::revision).max().orElseThrow();
        requireItem(item, rows.size(), maxRevision, hasher.hashIntradayBars(rows));
        requireRange(item, rows.getFirst().openTime(), rows.getLast().openTime());
    }

    private void validateDaily(InvestmentDatasetSnapshotItemPo item, List<MarketBarDailyRow> rows) {
        if (rows.isEmpty()) {
            throw new IllegalStateException("Backtest daily bars are missing");
        }
        long maxRevision = rows.stream().mapToLong(MarketBarDailyRow::revision).max().orElseThrow();
        requireItem(item, rows.size(), maxRevision, hasher.hashDailyBars(rows));
        requireRange(item, rows.getFirst().barDate().atStartOfDay().toInstant(ZoneOffset.UTC),
                rows.getLast().barDate().atStartOfDay().toInstant(ZoneOffset.UTC));
    }

    private MarketDataAsOfReader.AsOfBarSeries series(MarketDataAsOfReader.AsOfInstrument instrument,
                                                       InvestmentDatasetSnapshotItemPo item) {
        PriceType priceType = PriceType.valueOf(item.getPriceType());
        BarInterval interval = BarInterval.valueOf(item.getIntervalCode());
        return instrument.barSeries().stream()
                .filter(value -> value.priceType() == priceType && value.interval() == interval)
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "Backtest price series is missing: " + priceType + "/" + interval));
    }

    private BacktestInstrumentInput toInput(MarketDataAsOfReader.AsOfInstrument instrument,
                                            FrozenDataset frozen, StrategyDescriptor descriptor) {
        Map<String, Object> specification = required(frozen.specifications(), instrument.instrumentId(),
                "Frozen contract specification is missing");
        List<Map<String, Object>> tierValues = required(frozen.tiers(), instrument.instrumentId(),
                "Frozen position tiers are missing");
        Map<String, Object> fee = required(frozen.fees(), instrument.instrumentId(),
                "Frozen fee model is missing");
        List<PositionTier> tiers = tierValues.stream()
                .sorted(Comparator.comparingInt(value -> number(value, "tierLevel").intValue()))
                .map(value -> new PositionTier(number(value, "tierLevel").intValue(),
                        decimal(value, "startNotional"), decimal(value, "endNotional"),
                        decimal(value, "maxLeverage"), decimal(value, "maintenanceMarginRate")))
                .toList();
        if (tiers.isEmpty()) {
            throw new IllegalStateException("Frozen position tiers are missing");
        }
        BigDecimal maximumTierLeverage = tiers.stream().map(PositionTier::maximumLeverage)
                .min(BigDecimal::compareTo).orElseThrow();
        if (descriptor.defaultLeverage().compareTo(decimal(specification, "maxLeverage")) > 0
                || descriptor.defaultLeverage().compareTo(maximumTierLeverage) > 0) {
            throw new IllegalStateException("Strategy leverage exceeds the frozen contract or tier limit");
        }
        List<FuturesBar> signal = bars(instrument, descriptor.priceType());
        List<FuturesBar> market = bars(instrument, PriceType.MARKET);
        List<FuturesBar> mark = bars(instrument, PriceType.MARK);
        return new BacktestInstrumentInput(instrument.instrumentId(), new ContractTerms(
                decimal(specification, "priceEndStep"), decimal(specification, "quantityStep"),
                decimal(specification, "contractMultiplier")), decimal(fee, "makerFeeRate"),
                decimal(fee, "takerFeeRate"), descriptor.defaultLeverage(), tiers,
                signal, market, mark, instrument.fundingRates().stream()
                .map(value -> new FundingPoint(value.fundingTime(), value.fundingRate())).toList());
    }

    private List<FuturesBar> bars(MarketDataAsOfReader.AsOfInstrument instrument, PriceType priceType) {
        MarketDataAsOfReader.AsOfBarSeries series = instrument.barSeries().stream()
                .filter(value -> value.priceType() == priceType).findFirst()
                .orElseThrow(() -> new IllegalStateException("Backtest price type is missing: " + priceType));
        if (!series.intradayRows().isEmpty()) {
            return series.intradayRows().stream().map(this::bar).toList();
        }
        return series.dailyRows().stream().map(this::bar).toList();
    }

    private FuturesBar bar(MarketBarIntradayRow value) {
        return new FuturesBar(value.openTime(), value.closeTime(), value.openPrice(), value.highPrice(),
                value.lowPrice(), value.closePrice(), value.closed());
    }

    private FuturesBar bar(MarketBarDailyRow value) {
        return new FuturesBar(value.barDate().atStartOfDay().toInstant(ZoneOffset.UTC),
                value.barDate().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC),
                value.openPrice(), value.highPrice(), value.lowPrice(), value.closePrice(), value.closed());
    }

    private Map<String, Object> datasetManifest(InvestmentDatasetSnapshotPo snapshot,
                                                List<InvestmentDatasetSnapshotItemPo> items,
                                                StrategyDescriptor descriptor) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("sourceId", snapshot.getSourceId());
        manifest.put("instrumentIds", items.stream().map(InvestmentDatasetSnapshotItemPo::getInstrumentId)
                .distinct().sorted().toList());
        manifest.put("startTime", snapshot.getStartTime().toString());
        manifest.put("endTime", snapshot.getEndTime().toString());
        manifest.put("dataAsOf", snapshot.getDataAsOf().toString());
        manifest.put("strategyCode", descriptor.strategyCode());
        manifest.put("strategyVersion", descriptor.strategyVersion());
        manifest.put("contractSpecHash", snapshot.getContractSpecHash());
        manifest.put("positionTierHash", snapshot.getPositionTierHash());
        manifest.put("fundingDataHash", snapshot.getFundingDataHash());
        manifest.put("feeModel", hasher.canonicalizeJson(snapshot.getFeeModelSnapshotJson()));
        manifest.put("slippageModel", hasher.canonicalizeJson(snapshot.getSlippageModelSnapshotJson()));
        manifest.put("matchingModelCode", descriptor.matchingModelCode());
        manifest.put("items", items.stream().sorted(manifestOrder()).map(this::itemManifest).toList());
        return manifest;
    }

    private Comparator<InvestmentDatasetSnapshotItemPo> manifestOrder() {
        return Comparator.comparingLong(InvestmentDatasetSnapshotItemPo::getInstrumentId)
                .thenComparingInt(item -> dataTypeOrder(item.getDataType()))
                .thenComparingInt(item -> PriceType.valueOf(item.getPriceType()).ordinal())
                .thenComparingInt(item -> BarInterval.valueOf(item.getIntervalCode()).ordinal());
    }

    private static int dataTypeOrder(String value) {
        return switch (value) {
            case "CONTRACT_SPEC" -> 10;
            case "POSITION_TIER" -> 20;
            case "BAR_INTRADAY", "BAR_DAILY" -> 30;
            case "FUNDING_RATE" -> 40;
            default -> 100;
        };
    }

    private Map<String, Object> itemManifest(InvestmentDatasetSnapshotItemPo item) {
        return Map.of("instrumentId", item.getInstrumentId(), "dataType", item.getDataType(),
                "priceType", item.getPriceType(), "interval", item.getIntervalCode(),
                "firstTime", item.getFirstTime().toString(), "lastTime", item.getLastTime().toString(),
                "rowCount", item.getRowCount(), "maxRevision", item.getMaxRevision(),
                "dataHash", item.getDataHash());
    }

    private void requireItem(InvestmentDatasetSnapshotItemPo item, long rowCount,
                             long maxRevision, String dataHash) {
        if (item.getRowCount() != rowCount || item.getMaxRevision() != maxRevision) {
            throw new IllegalStateException("Backtest dataset item count or revision mismatch: "
                    + item.getDataType());
        }
        requireHash(item.getDataType(), item.getDataHash(), dataHash);
    }

    private static void requireRange(InvestmentDatasetSnapshotItemPo item, Instant first, Instant last) {
        if (!item.getFirstTime().equals(first) || !item.getLastTime().equals(last)) {
            throw new IllegalStateException("Backtest dataset item time range mismatch: " + item.getDataType());
        }
    }

    private static long maxFundingRevision(List<FundingRateRow> rows) {
        if (rows.isEmpty()) {
            throw new IllegalStateException("Backtest funding rates are missing");
        }
        return rows.stream().mapToLong(FundingRateRow::revision).max().orElseThrow();
    }

    private static void requireHash(String name, String expected, String actual) {
        if (expected == null || !expected.equals(actual)) {
            throw new IllegalStateException("Backtest " + name + " hash mismatch");
        }
    }

    private List<Map<String, Object>> readList(String json, String name) {
        try {
            return List.copyOf(objectMapper.readValue(json, MAP_LIST));
        } catch (JsonProcessingException | RuntimeException exception) {
            throw new IllegalStateException("Backtest " + name + " is invalid", exception);
        }
    }

    private Map<String, Object> readMap(String json, String name) {
        try {
            return Map.copyOf(objectMapper.readValue(json, MAP));
        } catch (JsonProcessingException | RuntimeException exception) {
            throw new IllegalStateException("Backtest " + name + " is invalid", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Object value, String name) {
        if (!(value instanceof List<?> list) || list.stream().anyMatch(item -> !(item instanceof Map<?, ?>))) {
            throw new IllegalStateException("Backtest " + name + " is invalid");
        }
        return list.stream().map(item -> (Map<String, Object>) item).toList();
    }

    private static String text(Map<String, Object> value, String key) {
        Object result = value.get(key);
        if (!(result instanceof String text) || text.isBlank()) {
            throw new IllegalStateException("Backtest snapshot field is invalid: " + key);
        }
        return text;
    }

    private static Number number(Map<String, Object> value, String key) {
        Object result = value.get(key);
        if (!(result instanceof Number number)) {
            throw new IllegalStateException("Backtest snapshot field is invalid: " + key);
        }
        return number;
    }

    private static BigDecimal decimal(Map<String, Object> value, String key) {
        Object result = value.get(key);
        try {
            return new BigDecimal(String.valueOf(result));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Backtest snapshot field is invalid: " + key, exception);
        }
    }

    private static <T> T required(Map<Long, T> values, long instrumentId, String message) {
        T value = values.get(instrumentId);
        if (value == null) {
            throw new IllegalStateException(message + ": " + instrumentId);
        }
        return value;
    }

    private record FrozenDataset(Map<Long, Map<String, Object>> specifications,
                                 Map<Long, List<Map<String, Object>>> tiers,
                                 Map<Long, Map<String, Object>> fees) {
    }
}
