package top.egon.mario.investment.marketdata.ingest.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.mario.investment.common.job.InvestmentJobClaim;
import top.egon.mario.investment.common.job.InvestmentJobNonRetryableException;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.InvestmentJobType;
import top.egon.mario.investment.marketdata.ingest.AbstractMarketDataJobHandler;
import top.egon.mario.investment.marketdata.ingest.MarketDataChecksum;
import top.egon.mario.investment.marketdata.ingest.MarketDataDimension;
import top.egon.mario.investment.marketdata.ingest.MarketDataDimensionResolver;
import top.egon.mario.investment.marketdata.ingest.MarketDataJobInput;
import top.egon.mario.investment.marketdata.event.InvestmentMarketDataCommittedEvent;
import top.egon.mario.investment.marketdata.event.MarketDataAfterCommitPublisher;
import top.egon.mario.investment.marketdata.provider.FundingRateProvider;
import top.egon.mario.investment.marketdata.provider.ProviderRegistry;
import top.egon.mario.investment.marketdata.provider.model.ExternalFundingRate;
import top.egon.mario.investment.marketdata.provider.model.FundingRateQuery;
import top.egon.mario.investment.marketdata.quality.MarketDataQualityService;
import top.egon.mario.investment.marketdata.repository.jdbc.FundingRateJdbcRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.model.FundingRateWrite;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketDataWriteContext;
import top.egon.mario.investment.marketdata.repository.jdbc.model.RevisionBatchResult;
import top.egon.mario.investment.marketdata.subscription.InvestmentMarketSubscriptionRegistry;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Revision-persists subscribed funding-rate observations without zero fallbacks.
 */
@Component
public class FundingRateSyncJobHandler extends AbstractMarketDataJobHandler<ExternalFundingRate> {

    private final ProviderRegistry providerRegistry;
    private final MarketDataDimensionResolver dimensionResolver;
    private final FundingRateJdbcRepository fundingRateRepository;
    private final MarketDataQualityService qualityService;
    private final MarketDataAfterCommitPublisher afterCommitPublisher;
    private final Clock clock;

    public FundingRateSyncJobHandler(ObjectMapper objectMapper,
                                     InvestmentMarketSubscriptionRegistry subscriptionRegistry,
                                     TransactionTemplate transactionTemplate,
                                     ProviderRegistry providerRegistry,
                                     MarketDataDimensionResolver dimensionResolver,
                                     FundingRateJdbcRepository fundingRateRepository,
                                     MarketDataQualityService qualityService,
                                     MarketDataAfterCommitPublisher afterCommitPublisher,
                                     Clock clock) {
        super(objectMapper, subscriptionRegistry, transactionTemplate, dimensionResolver, qualityService);
        this.providerRegistry = providerRegistry;
        this.dimensionResolver = dimensionResolver;
        this.fundingRateRepository = fundingRateRepository;
        this.qualityService = qualityService;
        this.afterCommitPublisher = afterCommitPublisher;
        this.clock = clock;
    }

    @Override
    public InvestmentJobType jobType() {
        return InvestmentJobType.FUNDING_RATE_INCREMENTAL;
    }

    @Override
    protected List<ExternalFundingRate> fetch(MarketDataJobInput input) {
        if (input.startInclusive() == null || input.endExclusive() == null) {
            throw new InvestmentJobNonRetryableException("MARKET_JOB_RANGE_REQUIRED",
                    "Funding-rate ingestion requires a time range");
        }
        return providerRegistry.require(input.sourceCode(), DataCapability.FUNDING_RATE, FundingRateProvider.class)
                .fundingRates(new FundingRateQuery(input.productType(), input.symbol(), input.startInclusive(),
                        input.endExclusive(), input.pageSize()));
    }

    @Override
    protected void validateSubscription(MarketDataJobInput input) {
        if (input.capability() != DataCapability.FUNDING_RATE) {
            throw new InvestmentJobNonRetryableException("MARKET_JOB_CAPABILITY_INVALID",
                    "Funding handler requires FUNDING_RATE");
        }
        subscriptionRegistry().requireCapability(input.sourceCode(), input.productType(), input.symbol(),
                DataCapability.FUNDING_RATE);
    }

    @Override
    protected void validatePage(MarketDataJobInput input, List<ExternalFundingRate> page) {
        page.forEach(rate -> {
            if (!input.sourceCode().equals(rate.sourceCode()) || input.productType() != rate.productType()
                    || !input.symbol().equals(rate.symbol())
                    || rate.fundingTime().isBefore(input.startInclusive())
                    || !rate.fundingTime().isBefore(input.endExclusive())
                    || !rate.fundingTime().equals(rate.fundingTime().truncatedTo(ChronoUnit.MILLIS))) {
                throw new InvestmentJobNonRetryableException("MARKET_PROVIDER_DIMENSION_MISMATCH",
                        "Provider returned a funding rate outside the requested dimension, half-open range, "
                                + "or Unix-millisecond precision");
            }
        });
    }

    @Override
    protected boolean providerPaged(MarketDataJobInput input) {
        return true;
    }

    @Override
    protected Instant nextPageStart(MarketDataJobInput input, List<ExternalFundingRate> page) {
        return successor(page.stream().map(ExternalFundingRate::fundingTime)
                .max(Instant::compareTo).orElseThrow());
    }

    @Override
    protected int persistPage(InvestmentJobClaim claim, MarketDataJobInput input, List<ExternalFundingRate> page,
                              List<ExternalFundingRate> previousPage) {
        MarketDataDimension dimension = dimensionResolver.resolve(input);
        Instant now = clock.instant();
        List<FundingRateWrite> writes = page.stream().map(rate -> new FundingRateWrite(
                dimension.sourceId(), dimension.instrumentId(), rate.fundingTime(), rate.rate(), now,
                MarketDataChecksum.sha256(rate.fundingTime() + "|"
                        + MarketDataChecksum.decimal(rate.rate())))).toList();
        Instant latestFundingTime = page.stream().map(ExternalFundingRate::fundingTime).max(Instant::compareTo)
                .orElseThrow();
        Instant nextStartTime = successor(latestFundingTime);
        RevisionBatchResult result = fundingRateRepository.writeBatch(new MarketDataWriteContext(claim.id(), now,
                nextStartTime), writes);
        int written = result.inserted() + result.revised();
        if (written > 0) {
            afterCommitPublisher.publishAfterCommit(new InvestmentMarketDataCommittedEvent(dimension.sourceId(),
                    dimension.instrumentId(), "FUNDING_RATE", written, latestFundingTime));
        }
        return written;
    }

    private Instant successor(Instant fundingTime) {
        return fundingTime.plusMillis(1);
    }
}
