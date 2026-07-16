package top.egon.mario.investment.marketdata.ingest.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.mario.investment.common.model.InvestmentJobType;
import top.egon.mario.investment.marketdata.ingest.MarketDataDimensionResolver;
import top.egon.mario.investment.marketdata.provider.ProviderRegistry;
import top.egon.mario.investment.marketdata.quality.MarketDataQualityService;
import top.egon.mario.investment.marketdata.event.MarketDataAfterCommitPublisher;
import top.egon.mario.investment.marketdata.repository.jdbc.MarketBarJdbcRepository;
import top.egon.mario.investment.marketdata.subscription.InvestmentMarketSubscriptionRegistry;

import java.time.Clock;

/**
 * Uses the same fenced candle pipeline for bounded historical backfills.
 */
@Component
public class BarBackfillJobHandler extends CandleSyncJobHandler {

    public BarBackfillJobHandler(ObjectMapper objectMapper,
                                 InvestmentMarketSubscriptionRegistry subscriptionRegistry,
                                 TransactionTemplate transactionTemplate,
                                 ProviderRegistry providerRegistry,
                                 MarketDataDimensionResolver dimensionResolver,
                                 MarketBarJdbcRepository barRepository,
                                 MarketDataQualityService qualityService,
                                 MarketDataAfterCommitPublisher afterCommitPublisher,
                                 Clock clock) {
        super(objectMapper, subscriptionRegistry, transactionTemplate, providerRegistry, dimensionResolver,
                barRepository, qualityService, afterCommitPublisher, clock);
    }

    @Override
    public InvestmentJobType jobType() {
        return InvestmentJobType.BAR_BACKFILL;
    }
}
