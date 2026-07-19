package top.egon.mario.investment.marketdata.ingest.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.InvestmentJobType;
import top.egon.mario.investment.marketdata.ingest.MarketDataBackfillWindowPolicy;
import top.egon.mario.investment.marketdata.ingest.MarketDataDimensionResolver;
import top.egon.mario.investment.marketdata.ingest.MarketDataJobInput;
import top.egon.mario.investment.marketdata.provider.ProviderRegistry;
import top.egon.mario.investment.marketdata.quality.MarketDataQualityService;
import top.egon.mario.investment.marketdata.event.MarketDataAfterCommitPublisher;
import top.egon.mario.investment.marketdata.repository.jdbc.MarketBarJdbcRepository;
import top.egon.mario.investment.marketdata.subscription.InvestmentMarketSubscriptionRegistry;

import java.time.Clock;
import java.time.Duration;

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

    @Override
    protected MarketDataJobInput executionInput(MarketDataJobInput input) {
        if (!"SCHEDULED".equals(input.triggerSource()) || input.interval() != BarInterval.M1) {
            return input;
        }
        Duration maximumWindow = MarketDataBackfillWindowPolicy.initialJobWindow(
                input.interval(), input.pageSize());
        if (Duration.between(input.startInclusive(), input.endExclusive()).compareTo(maximumWindow) <= 0) {
            return input;
        }
        return input.withRange(input.endExclusive().minus(maximumWindow), input.endExclusive());
    }
}
