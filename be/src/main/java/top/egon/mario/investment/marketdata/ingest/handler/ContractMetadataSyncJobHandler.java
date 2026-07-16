package top.egon.mario.investment.marketdata.ingest.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.mario.investment.common.job.InvestmentJobClaim;
import top.egon.mario.investment.common.job.InvestmentJobNonRetryableException;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.InvestmentJobType;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.marketdata.ingest.AbstractMarketDataJobHandler;
import top.egon.mario.investment.marketdata.ingest.MarketDataChecksum;
import top.egon.mario.investment.marketdata.ingest.MarketDataCursorService;
import top.egon.mario.investment.marketdata.ingest.MarketDataDimension;
import top.egon.mario.investment.marketdata.ingest.MarketDataDimensionResolver;
import top.egon.mario.investment.marketdata.ingest.MarketDataJobInput;
import top.egon.mario.investment.marketdata.event.InvestmentMarketDataCommittedEvent;
import top.egon.mario.investment.marketdata.event.MarketDataAfterCommitPublisher;
import top.egon.mario.investment.marketdata.po.InvestmentDataSourcePo;
import top.egon.mario.investment.marketdata.po.InvestmentContractSpecPo;
import top.egon.mario.investment.marketdata.po.InvestmentInstrumentPo;
import top.egon.mario.investment.marketdata.po.InvestmentInstrumentSourcePo;
import top.egon.mario.investment.marketdata.provider.ContractMetadataProvider;
import top.egon.mario.investment.marketdata.provider.ProviderRegistry;
import top.egon.mario.investment.marketdata.provider.model.ExternalContract;
import top.egon.mario.investment.marketdata.quality.MarketDataQualityService;
import top.egon.mario.investment.marketdata.repository.InvestmentDataSourceRepository;
import top.egon.mario.investment.marketdata.repository.InvestmentContractSpecRepository;
import top.egon.mario.investment.marketdata.repository.InvestmentInstrumentRepository;
import top.egon.mario.investment.marketdata.repository.InvestmentInstrumentSourceRepository;
import top.egon.mario.investment.marketdata.subscription.InvestmentMarketSubscriptionRegistry;
import top.egon.mario.investment.marketdata.subscription.MarketSubscription;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Synchronizes provider-backed instrument identity, complete contract specification and ingestion dimensions.
 */
@Component
public class ContractMetadataSyncJobHandler extends AbstractMarketDataJobHandler<ExternalContract> {

    private final ProviderRegistry providerRegistry;
    private final InvestmentDataSourceRepository dataSourceRepository;
    private final InvestmentInstrumentRepository instrumentRepository;
    private final InvestmentInstrumentSourceRepository instrumentSourceRepository;
    private final InvestmentContractSpecRepository contractSpecRepository;
    private final MarketDataCursorService cursorService;
    private final ObjectMapper objectMapper;
    private final MarketDataAfterCommitPublisher afterCommitPublisher;

    public ContractMetadataSyncJobHandler(ObjectMapper objectMapper,
                                          InvestmentMarketSubscriptionRegistry subscriptionRegistry,
                                          TransactionTemplate transactionTemplate,
                                          MarketDataDimensionResolver dimensionResolver,
                                          MarketDataQualityService qualityService,
                                          ProviderRegistry providerRegistry,
                                          InvestmentDataSourceRepository dataSourceRepository,
                                          InvestmentInstrumentRepository instrumentRepository,
                                          InvestmentInstrumentSourceRepository instrumentSourceRepository,
                                          InvestmentContractSpecRepository contractSpecRepository,
                                          MarketDataCursorService cursorService,
                                          MarketDataAfterCommitPublisher afterCommitPublisher) {
        super(objectMapper, subscriptionRegistry, transactionTemplate, dimensionResolver, qualityService);
        this.providerRegistry = providerRegistry;
        this.dataSourceRepository = dataSourceRepository;
        this.instrumentRepository = instrumentRepository;
        this.instrumentSourceRepository = instrumentSourceRepository;
        this.contractSpecRepository = contractSpecRepository;
        this.cursorService = cursorService;
        this.objectMapper = objectMapper;
        this.afterCommitPublisher = afterCommitPublisher;
    }

    @Override
    public InvestmentJobType jobType() {
        return InvestmentJobType.CONTRACT_SYNC;
    }

    @Override
    protected List<ExternalContract> fetch(MarketDataJobInput input) {
        return providerRegistry.require(input.sourceCode(), DataCapability.CONTRACT_METADATA,
                ContractMetadataProvider.class).contracts(input.productType(), Set.of(input.symbol()));
    }

    @Override
    protected void validateSubscription(MarketDataJobInput input) {
        if (input.capability() != DataCapability.CONTRACT_METADATA) {
            throw new InvestmentJobNonRetryableException("MARKET_JOB_CAPABILITY_INVALID",
                    "CONTRACT_SYNC requires CONTRACT_METADATA");
        }
        subscriptionRegistry().requireCapability(input.sourceCode(), input.productType(), input.symbol(),
                DataCapability.CONTRACT_METADATA);
    }

    @Override
    protected void validatePage(MarketDataJobInput input, List<ExternalContract> page) {
        if (page.size() != 1) {
            throw new InvestmentJobNonRetryableException("MARKET_CONTRACT_METADATA_MISSING",
                    "Provider must return exactly the subscribed contract metadata record");
        }
        requireSameDimension(input, page.getFirst());
    }

    @Override
    protected int persistPage(InvestmentJobClaim claim, MarketDataJobInput input, List<ExternalContract> page,
                              List<ExternalContract> previousPage) {
        InvestmentDataSourcePo source = dataSourceRepository.findByCodeAndDeletedFalse(input.sourceCode())
                .orElseThrow(() -> new InvestmentJobNonRetryableException("MARKET_SOURCE_NOT_FOUND",
                        "Market-data source is not registered: " + input.sourceCode()));
        int written = 0;
        for (ExternalContract contract : page) {
            InvestmentInstrumentPo instrument = instrumentRepository
                    .findByVenueIdAndProductTypeAndSymbolAndDeletedFalse(
                            source.getVenueId(), input.productType(), input.symbol())
                    .orElseGet(InvestmentInstrumentPo::new);
            boolean newInstrument = instrument.getId() == null;
            if (newInstrument) {
                mapInstrument(instrument, source.getVenueId(), contract);
                instrument = instrumentRepository.save(instrument);
            }

            MarketDataDimension dimension = new MarketDataDimension(source.getId(), instrument.getId());
            seedCursors(dimension, input);
            MarketDataCursorService.LockedCursor cursor = cursorService.lock(dimension, "CONTRACT_METADATA",
                    PriceType.NONE, BarInterval.NONE);
            String normalizedJson = normalizedBusinessJson(contract);
            InvestmentContractSpecPo current = contractSpecRepository.findById(instrument.getId()).orElse(null);
            boolean sameBusiness = current != null && Objects.equals(current.getSourceId(), source.getId())
                    && sameBusinessJson(normalizedJson, current.getRawMetadataJson());
            validateSourceOrder(current, contract, sameBusiness);

            if (!newInstrument) {
                mapInstrument(instrument, source.getVenueId(), contract);
                instrumentRepository.save(instrument);
            }

            InvestmentInstrumentSourcePo mapping = instrumentSourceRepository
                    .findByInstrumentIdAndSourceIdAndDeletedFalse(instrument.getId(), source.getId())
                    .orElseGet(InvestmentInstrumentSourcePo::new);
            mapping.setInstrumentId(instrument.getId());
            mapping.setSourceId(source.getId());
            mapping.setExternalSymbol(contract.symbol());
            mapping.setExternalProductType(contract.productType().name());
            mapping.setSourceStatus("ACTIVE");
            mapping.setRawMetadataJson(normalizedJson);
            mapping.setLastSyncedAt(cursor.completedAt());
            instrumentSourceRepository.save(mapping);

            String checksum = MarketDataChecksum.sha256(normalizedJson);
            boolean changed = current == null || !sameBusiness;
            if (changed) {
                InvestmentContractSpecPo spec = current == null ? new InvestmentContractSpecPo() : current;
                mapSpec(spec, source.getId(), instrument.getId(), contract, normalizedJson,
                        cursor.completedAt(), current == null ? 1L : current.getRevision() + 1L);
                contractSpecRepository.save(spec);
                afterCommitPublisher.publishAfterCommit(new InvestmentMarketDataCommittedEvent(source.getId(),
                        instrument.getId(), "CONTRACT_METADATA", 1, contract.observedAt()));
                written++;
            } else {
                current.setSourceUpdatedAt(contract.observedAt());
                current.setIngestedAt(cursor.completedAt());
                current.setRawMetadataJson(normalizedJson);
                contractSpecRepository.save(current);
            }
            cursorService.completeLocked(cursor, null, checksum);
        }
        return written;
    }

    @Override
    protected String resultJson(int fetched, int written) {
        return "{\"fetched\":" + fetched + ",\"identityRecords\":" + fetched
                + ",\"contractSpecsWritten\":" + written + "}";
    }

    private void seedCursors(MarketDataDimension dimension, MarketDataJobInput input) {
        MarketSubscription subscription = subscriptionRegistry().requireCapability(input.sourceCode(),
                input.productType(), input.symbol(), DataCapability.CONTRACT_METADATA);
        for (DataCapability capability : subscription.capabilities()) {
            if (isCandle(capability)) {
                PriceType priceType = priceType(capability);
                for (BarInterval interval : subscription.intervals()) {
                    cursorService.seedIfAbsent(dimension,
                            interval == BarInterval.D1 ? "BAR_DAILY" : "BAR_INTRADAY", priceType, interval);
                }
            } else {
                cursorService.seedIfAbsent(dimension, dataType(capability), PriceType.NONE, BarInterval.NONE);
            }
        }
    }

    private void mapInstrument(InvestmentInstrumentPo instrument, long venueId, ExternalContract contract) {
        instrument.setVenueId(venueId);
        instrument.setProductType(contract.productType());
        instrument.setContractType(contract.contractType());
        instrument.setSymbol(contract.symbol());
        instrument.setBaseAsset(contract.baseAsset());
        instrument.setQuoteAsset(contract.quoteAsset());
        instrument.setSettlementAsset(contract.settleAsset());
        instrument.setMarginAsset(contract.settleAsset());
        instrument.setStatus("ACTIVE");
    }

    private void validateSourceOrder(InvestmentContractSpecPo current, ExternalContract contract,
                                     boolean sameBusiness) {
        if (current == null || current.getSourceUpdatedAt() == null) {
            return;
        }
        int order = contract.observedAt().compareTo(current.getSourceUpdatedAt());
        if (order < 0) {
            throw new InvestmentJobNonRetryableException("MARKET_CONTRACT_METADATA_STALE",
                    "Contract metadata observedAt moved backwards");
        }
        if (order == 0 && !sameBusiness) {
            throw new InvestmentJobNonRetryableException("MARKET_CONTRACT_METADATA_OBSERVED_AT_CONFLICT",
                    "Contract metadata content changed at an already persisted observedAt");
        }
    }

    private String dataType(DataCapability capability) {
        return switch (capability) {
            case CONTRACT_METADATA -> "CONTRACT_METADATA";
            case POSITION_TIER -> "POSITION_TIER";
            case LATEST_TICKER, OPEN_INTEREST -> "QUOTE";
            case FUNDING_RATE -> "FUNDING_RATE";
            case MARKET_CANDLE, MARK_CANDLE, INDEX_CANDLE -> throw new IllegalArgumentException("Candle data type");
        };
    }

    private boolean isCandle(DataCapability capability) {
        return capability == DataCapability.MARKET_CANDLE || capability == DataCapability.MARK_CANDLE
                || capability == DataCapability.INDEX_CANDLE;
    }

    private PriceType priceType(DataCapability capability) {
        return switch (capability) {
            case MARKET_CANDLE -> PriceType.MARKET;
            case MARK_CANDLE -> PriceType.MARK;
            case INDEX_CANDLE -> PriceType.INDEX;
            default -> throw new IllegalArgumentException("Not a candle capability: " + capability);
        };
    }

    private void requireSameDimension(MarketDataJobInput input, ExternalContract contract) {
        if (!input.sourceCode().equals(contract.sourceCode()) || input.productType() != contract.productType()
                || !input.symbol().equals(contract.symbol())) {
            throw new InvestmentJobNonRetryableException("MARKET_PROVIDER_DIMENSION_MISMATCH",
                    "Provider returned contract metadata outside the requested subscription");
        }
    }

    private void mapSpec(InvestmentContractSpecPo spec, long sourceId, long instrumentId,
                         ExternalContract contract, String normalizedJson, Instant ingestedAt, long revision) {
        spec.setInstrumentId(instrumentId);
        spec.setSourceId(sourceId);
        spec.setPricePrecision(contract.pricePrecision());
        spec.setQuantityPrecision(contract.quantityPrecision());
        spec.setPriceEndStep(contract.priceEndStep());
        spec.setQuantityStep(contract.quantityStep());
        spec.setContractMultiplier(contract.contractMultiplier());
        spec.setMinTradeQuantity(contract.minTradeQuantity());
        spec.setMinTradeNotional(contract.minTradeNotional());
        spec.setMaxMarketOrderQuantity(contract.maxMarketOrderQuantity());
        spec.setMaxLimitOrderQuantity(contract.maxLimitOrderQuantity());
        spec.setMakerFeeRate(contract.makerFeeRate());
        spec.setTakerFeeRate(contract.takerFeeRate());
        spec.setMinLeverage(contract.minLeverage());
        spec.setMaxLeverage(contract.maxLeverage());
        spec.setFundingIntervalHours(contract.fundingIntervalHours());
        spec.setBuyLimitPriceRatio(contract.buyLimitPriceRatio());
        spec.setSellLimitPriceRatio(contract.sellLimitPriceRatio());
        spec.setSourceUpdatedAt(contract.observedAt());
        spec.setIngestedAt(ingestedAt);
        spec.setRevision(revision);
        spec.setRawMetadataJson(normalizedJson);
    }

    private String normalizedBusinessJson(ExternalContract contract) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("sourceCode", contract.sourceCode());
        normalized.put("productType", contract.productType().name());
        normalized.put("symbol", contract.symbol());
        normalized.put("contractType", contract.contractType().name());
        normalized.put("baseAsset", contract.baseAsset());
        normalized.put("quoteAsset", contract.quoteAsset());
        normalized.put("settleAsset", contract.settleAsset());
        normalized.put("pricePrecision", contract.pricePrecision());
        normalized.put("quantityPrecision", contract.quantityPrecision());
        normalized.put("priceEndStep", MarketDataChecksum.decimal(contract.priceEndStep()));
        normalized.put("quantityStep", MarketDataChecksum.decimal(contract.quantityStep()));
        normalized.put("contractMultiplier", MarketDataChecksum.decimal(contract.contractMultiplier()));
        normalized.put("minTradeQuantity", MarketDataChecksum.decimal(contract.minTradeQuantity()));
        normalized.put("minTradeNotional", MarketDataChecksum.decimal(contract.minTradeNotional()));
        normalized.put("maxMarketOrderQuantity",
                MarketDataChecksum.decimal(contract.maxMarketOrderQuantity()));
        normalized.put("maxLimitOrderQuantity", MarketDataChecksum.decimal(contract.maxLimitOrderQuantity()));
        normalized.put("makerFeeRate", MarketDataChecksum.decimal(contract.makerFeeRate()));
        normalized.put("takerFeeRate", MarketDataChecksum.decimal(contract.takerFeeRate()));
        normalized.put("minLeverage", MarketDataChecksum.decimal(contract.minLeverage()));
        normalized.put("maxLeverage", MarketDataChecksum.decimal(contract.maxLeverage()));
        normalized.put("fundingIntervalHours", contract.fundingIntervalHours());
        normalized.put("buyLimitPriceRatio", MarketDataChecksum.decimal(contract.buyLimitPriceRatio()));
        normalized.put("sellLimitPriceRatio", MarketDataChecksum.decimal(contract.sellLimitPriceRatio()));
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to encode normalized contract metadata", ex);
        }
    }

    private boolean sameBusinessJson(String normalizedJson, String persistedJson) {
        if (persistedJson == null) {
            return false;
        }
        try {
            return objectMapper.readTree(normalizedJson).equals(objectMapper.readTree(persistedJson));
        } catch (JsonProcessingException ex) {
            return false;
        }
    }
}
