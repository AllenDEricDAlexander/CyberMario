package top.egon.mario.investment.marketdata.ingest.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.mario.investment.common.model.InvestmentJobType;
import top.egon.mario.investment.marketdata.ingest.MarketDataDimensionResolver;
import top.egon.mario.investment.marketdata.provider.ProviderRegistry;
import top.egon.mario.investment.marketdata.quality.MarketDataQualityService;
import top.egon.mario.investment.marketdata.event.MarketDataAfterCommitPublisher;
import top.egon.mario.investment.marketdata.repository.jdbc.FundingRateJdbcRepository;
import top.egon.mario.investment.marketdata.subscription.InvestmentMarketSubscriptionRegistry;

import java.time.Clock;

/**
 * Uses the same revision pipeline for bounded funding-rate backfills.
 */
@Component
public class FundingRateBackfillJobHandler extends FundingRateSyncJobHandler {

    public FundingRateBackfillJobHandler(ObjectMapper objectMapper,
                                         InvestmentMarketSubscriptionRegistry subscriptionRegistry,
                                         TransactionTemplate transactionTemplate,
                                         ProviderRegistry providerRegistry,
                                         MarketDataDimensionResolver dimensionResolver,
                                         FundingRateJdbcRepository fundingRateRepository,
                                         MarketDataQualityService qualityService,
                                         MarketDataAfterCommitPublisher afterCommitPublisher,
                                         Clock clock) {
        super(objectMapper, subscriptionRegistry, transactionTemplate, providerRegistry, dimensionResolver,
                fundingRateRepository, qualityService, afterCommitPublisher, clock);
    }

    @Override
    public InvestmentJobType jobType() {
        return InvestmentJobType.FUNDING_RATE_BACKFILL;
    }
}
