package top.egon.mario.investment.marketdata.ingest.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.mario.investment.common.job.InvestmentJobClaim;
import top.egon.mario.investment.common.job.InvestmentJobNonRetryableException;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.InvestmentJobType;
import top.egon.mario.investment.marketdata.ingest.AbstractMarketDataJobHandler;
import top.egon.mario.investment.marketdata.ingest.MarketDataChecksum;
import top.egon.mario.investment.marketdata.ingest.MarketDataDimension;
import top.egon.mario.investment.marketdata.ingest.MarketDataDimensionResolver;
import top.egon.mario.investment.marketdata.ingest.MarketDataJobInput;
import top.egon.mario.investment.marketdata.event.InvestmentMarketDataCommittedEvent;
import top.egon.mario.investment.marketdata.event.MarketDataAfterCommitPublisher;
import top.egon.mario.investment.marketdata.provider.ContractCandleProvider;
import top.egon.mario.investment.marketdata.provider.ProviderRegistry;
import top.egon.mario.investment.marketdata.provider.model.CandleQuery;
import top.egon.mario.investment.marketdata.provider.model.ExternalCandle;
import top.egon.mario.investment.marketdata.quality.MarketDataQualityService;
import top.egon.mario.investment.marketdata.repository.jdbc.MarketBarJdbcRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketBarDailyWrite;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketBarIntradayWrite;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketDataWriteContext;
import top.egon.mario.investment.marketdata.repository.jdbc.model.RevisionBatchResult;
import top.egon.mario.investment.marketdata.subscription.InvestmentMarketSubscriptionRegistry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fetches and revision-persists one subscribed candle dimension.
 */
@Component
public class CandleSyncJobHandler extends AbstractMarketDataJobHandler<ExternalCandle> {

    private final ProviderRegistry providerRegistry;
    private final MarketDataDimensionResolver dimensionResolver;
    private final MarketBarJdbcRepository barRepository;
    private final MarketDataQualityService qualityService;
    private final MarketDataAfterCommitPublisher afterCommitPublisher;
    private final Clock clock;

    public CandleSyncJobHandler(ObjectMapper objectMapper,
                                InvestmentMarketSubscriptionRegistry subscriptionRegistry,
                                TransactionTemplate transactionTemplate,
                                ProviderRegistry providerRegistry,
                                MarketDataDimensionResolver dimensionResolver,
                                MarketBarJdbcRepository barRepository,
                                MarketDataQualityService qualityService,
                                MarketDataAfterCommitPublisher afterCommitPublisher,
                                Clock clock) {
        super(objectMapper, subscriptionRegistry, transactionTemplate, dimensionResolver, qualityService);
        this.providerRegistry = providerRegistry;
        this.dimensionResolver = dimensionResolver;
        this.barRepository = barRepository;
        this.qualityService = qualityService;
        this.afterCommitPublisher = afterCommitPublisher;
        this.clock = clock;
    }

    @Override
    public InvestmentJobType jobType() {
        return InvestmentJobType.BAR_INCREMENTAL;
    }

    @Override
    protected List<ExternalCandle> fetch(MarketDataJobInput input) {
        requireRange(input);
        return providerRegistry.require(input.sourceCode(), input.capability(), ContractCandleProvider.class)
                .candles(new CandleQuery(input.productType(), input.symbol(), input.priceType(), input.interval(),
                        input.startInclusive(), input.endExclusive(), input.pageSize()));
    }

    @Override
    protected void validateSubscription(MarketDataJobInput input) {
        if (!isCandle(input.capability())) {
            throw new InvestmentJobNonRetryableException("MARKET_JOB_CAPABILITY_INVALID",
                    "BAR_INCREMENTAL requires a candle capability");
        }
        subscriptionRegistry().requireCandle(input.sourceCode(), input.productType(), input.symbol(),
                input.interval(), input.priceType());
    }

    @Override
    protected void validatePage(InvestmentJobClaim claim, MarketDataJobInput input, List<ExternalCandle> page) {
        for (ExternalCandle candle : page) {
            if (!input.sourceCode().equals(candle.sourceCode()) || input.productType() != candle.productType()
                    || !input.symbol().equals(candle.symbol()) || input.priceType() != candle.priceType()
                    || input.interval() != candle.interval()
                    || candle.openTime().isBefore(input.startInclusive())
                    || !candle.openTime().isBefore(input.endExclusive())) {
                throw new InvestmentJobNonRetryableException("MARKET_PROVIDER_DIMENSION_MISMATCH",
                        "Provider returned a candle outside the requested dimension or half-open range");
            }
        }
        var timingFindings = qualityService.inspectCandleTiming(page);
        if (!timingFindings.isEmpty()) {
            persistQualityAudit(claim, input, timingFindings);
            throw new InvestmentJobNonRetryableException("MARKET_PROVIDER_CANDLE_TIME_INVALID",
                    "Provider returned a candle with an invalid interval boundary");
        }
    }

    @Override
    protected boolean providerPaged(MarketDataJobInput input) {
        return true;
    }

    @Override
    protected Instant nextPageStart(MarketDataJobInput input, List<ExternalCandle> page) {
        Instant latest = page.stream().map(ExternalCandle::openTime).max(Instant::compareTo).orElseThrow();
        return latest.plus(intervalDuration(input.interval()));
    }

    @Override
    protected int persistPage(InvestmentJobClaim claim, MarketDataJobInput input, List<ExternalCandle> page,
                              List<ExternalCandle> previousPage) {
        if (page.isEmpty()) {
            return 0;
        }
        MarketDataDimension dimension = dimensionResolver.resolve(input);
        List<top.egon.mario.investment.marketdata.quality.MarketDataQualityFinding> findings = new ArrayList<>(
                qualityService.inspectCandles(page));
        if (!previousPage.isEmpty()) {
            ExternalCandle previous = previousPage.stream().max(java.util.Comparator.comparing(
                    ExternalCandle::openTime)).orElseThrow();
            ExternalCandle current = page.stream().min(java.util.Comparator.comparing(
                    ExternalCandle::openTime)).orElseThrow();
            findings.addAll(qualityService.inspectCandles(List.of(previous, current)).stream()
                    .filter(finding -> finding.code()
                            == top.egon.mario.investment.marketdata.quality.MarketDataQualityCode.GAP
                            || finding.code()
                            == top.egon.mario.investment.marketdata.quality.MarketDataQualityCode.DUPLICATE)
                    .toList());
        }
        qualityService.persist(claim.id(), new MarketDataQualityService.MarketDataDimensionRef(
                dimension.sourceId(), dimension.instrumentId()), findings);
        List<ExternalCandle> unique = unique(page);
        Instant ingestedAt = clock.instant();
        MarketDataWriteContext context = new MarketDataWriteContext(claim.id(), ingestedAt,
                unique.getLast().closeTime());
        RevisionBatchResult result = input.interval() == BarInterval.D1
                ? barRepository.writeDailyBatch(context, unique.stream()
                        .map(candle -> daily(dimension, candle, ingestedAt)).toList())
                : barRepository.writeIntradayBatch(context, unique.stream()
                        .map(candle -> intraday(dimension, candle, ingestedAt)).toList());
        int written = result.inserted() + result.revised();
        if (written > 0) {
            afterCommitPublisher.publishAfterCommit(new InvestmentMarketDataCommittedEvent(dimension.sourceId(),
                    dimension.instrumentId(), "BAR", written, unique.getLast().closeTime()));
        }
        return written;
    }

    private List<ExternalCandle> unique(List<ExternalCandle> page) {
        Map<Instant, ExternalCandle> values = new LinkedHashMap<>();
        page.stream().sorted(java.util.Comparator.comparing(ExternalCandle::openTime))
                .forEach(candle -> values.putIfAbsent(candle.openTime(), candle));
        return List.copyOf(values.values());
    }

    private MarketBarIntradayWrite intraday(MarketDataDimension dimension, ExternalCandle candle,
                                             Instant ingestedAt) {
        return new MarketBarIntradayWrite(dimension.sourceId(), dimension.instrumentId(), candle.priceType(),
                candle.interval(), candle.openTime(), candle.closeTime(), candle.open(), candle.high(), candle.low(),
                candle.close(), candle.baseVolume(), candle.quoteVolume(), candle.closed(), candle.observedAt(),
                ingestedAt, checksum(candle));
    }

    private MarketBarDailyWrite daily(MarketDataDimension dimension, ExternalCandle candle, Instant ingestedAt) {
        return new MarketBarDailyWrite(dimension.sourceId(), dimension.instrumentId(), candle.priceType(),
                candle.openTime().atZone(ZoneOffset.UTC).toLocalDate(), candle.open(), candle.high(), candle.low(),
                candle.close(), candle.baseVolume(), candle.quoteVolume(), candle.closed(), candle.observedAt(),
                ingestedAt, checksum(candle));
    }

    private String checksum(ExternalCandle candle) {
        return MarketDataChecksum.sha256(MarketDataChecksum.decimal(candle.open()) + "|"
                + MarketDataChecksum.decimal(candle.high()) + "|" + MarketDataChecksum.decimal(candle.low()) + "|"
                + MarketDataChecksum.decimal(candle.close()) + "|"
                + MarketDataChecksum.decimal(candle.baseVolume()) + "|"
                + MarketDataChecksum.decimal(candle.quoteVolume()) + "|" + candle.closed());
    }

    private void requireRange(MarketDataJobInput input) {
        if (input.startInclusive() == null || input.endExclusive() == null) {
            throw new InvestmentJobNonRetryableException("MARKET_JOB_RANGE_REQUIRED",
                    "Candle ingestion requires a time range");
        }
    }

    private boolean isCandle(DataCapability capability) {
        return capability == DataCapability.MARKET_CANDLE || capability == DataCapability.MARK_CANDLE
                || capability == DataCapability.INDEX_CANDLE;
    }

    private Duration intervalDuration(BarInterval interval) {
        return switch (interval) {
            case M1 -> Duration.ofMinutes(1);
            case M5 -> Duration.ofMinutes(5);
            case M15 -> Duration.ofMinutes(15);
            case M30 -> Duration.ofMinutes(30);
            case H1 -> Duration.ofHours(1);
            case H4 -> Duration.ofHours(4);
            case D1 -> Duration.ofDays(1);
            case NONE -> throw new IllegalArgumentException("Concrete interval is required");
        };
    }
}
