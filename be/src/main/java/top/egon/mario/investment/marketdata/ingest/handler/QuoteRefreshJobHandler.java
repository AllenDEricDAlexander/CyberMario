package top.egon.mario.investment.marketdata.ingest.handler;

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
import top.egon.mario.investment.marketdata.provider.ContractTickerProvider;
import top.egon.mario.investment.marketdata.provider.ProviderRegistry;
import top.egon.mario.investment.marketdata.provider.model.ExternalContractTicker;
import top.egon.mario.investment.marketdata.quality.MarketDataQualityService;
import top.egon.mario.investment.marketdata.repository.jdbc.ContractQuoteJdbcRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.model.ContractQuoteWrite;
import top.egon.mario.investment.marketdata.service.QuoteCacheService;
import top.egon.mario.investment.marketdata.subscription.InvestmentMarketSubscriptionRegistry;
import top.egon.mario.investment.marketdata.subscription.MarketSubscription;

import java.util.List;
import java.util.Set;

/**
 * Persists latest quotes, then schedules cache and event work on transaction commit.
 */
@Component
public class QuoteRefreshJobHandler extends AbstractMarketDataJobHandler<ExternalContractTicker> {

    private final ProviderRegistry providerRegistry;
    private final MarketDataDimensionResolver dimensionResolver;
    private final ContractQuoteJdbcRepository quoteRepository;
    private final MarketDataCursorService cursorService;
    private final MarketDataQualityService qualityService;
    private final QuoteCacheService quoteCacheService;

    public QuoteRefreshJobHandler(ObjectMapper objectMapper,
                                  InvestmentMarketSubscriptionRegistry subscriptionRegistry,
                                  TransactionTemplate transactionTemplate,
                                  ProviderRegistry providerRegistry,
                                  MarketDataDimensionResolver dimensionResolver,
                                  ContractQuoteJdbcRepository quoteRepository,
                                  MarketDataCursorService cursorService,
                                  MarketDataQualityService qualityService,
                                  QuoteCacheService quoteCacheService) {
        super(objectMapper, subscriptionRegistry, transactionTemplate, dimensionResolver, qualityService);
        this.providerRegistry = providerRegistry;
        this.dimensionResolver = dimensionResolver;
        this.quoteRepository = quoteRepository;
        this.cursorService = cursorService;
        this.qualityService = qualityService;
        this.quoteCacheService = quoteCacheService;
    }

    @Override
    public InvestmentJobType jobType() {
        return InvestmentJobType.QUOTE_REFRESH;
    }

    @Override
    protected List<ExternalContractTicker> fetch(MarketDataJobInput input) {
        return providerRegistry.require(input.sourceCode(), input.capability(),
                ContractTickerProvider.class).tickers(input.productType(), Set.of(input.symbol()));
    }

    @Override
    protected void validateSubscription(MarketDataJobInput input) {
        if (input.capability() != DataCapability.LATEST_TICKER
                && input.capability() != DataCapability.CURRENT_FUNDING_RATE
                && input.capability() != DataCapability.OPEN_INTEREST) {
            throw new InvestmentJobNonRetryableException("MARKET_JOB_CAPABILITY_INVALID",
                    "Quote handler requires LATEST_TICKER, CURRENT_FUNDING_RATE, or OPEN_INTEREST");
        }
        subscriptionRegistry().requireCapability(input.sourceCode(), input.productType(), input.symbol(),
                input.capability());
    }

    @Override
    protected void validatePage(MarketDataJobInput input, List<ExternalContractTicker> page) {
        page.forEach(quote -> {
            if (!input.sourceCode().equals(quote.sourceCode()) || input.productType() != quote.productType()
                    || !input.symbol().equals(quote.symbol())) {
                throw new InvestmentJobNonRetryableException("MARKET_PROVIDER_DIMENSION_MISMATCH",
                        "Provider returned a quote outside the requested subscription");
            }
        });
    }

    @Override
    protected int persistPage(InvestmentJobClaim claim, MarketDataJobInput input,
                              List<ExternalContractTicker> page, List<ExternalContractTicker> previousPage) {
        MarketSubscription subscription = subscriptionRegistry().requireCapability(input.sourceCode(),
                input.productType(), input.symbol(), input.capability());
        MarketDataDimension dimension = dimensionResolver.resolve(input);
        MarketDataCursorService.LockedCursor cursor = cursorService.lock(dimension, "QUOTE", PriceType.NONE,
                BarInterval.NONE);
        if (page.isEmpty()) {
            qualityService.persist(claim.id(), new MarketDataQualityService.MarketDataDimensionRef(
                            dimension.sourceId(), dimension.instrumentId()),
                    qualityService.missingQuoteInputs(cursor.completedAt(), subscription.capabilities(), null, null));
            return 0;
        }
        int written = 0;
        for (ExternalContractTicker ticker : page) {
            ContractQuoteWrite quote = new ContractQuoteWrite(dimension.sourceId(), dimension.instrumentId(),
                    ticker.lastPrice(), ticker.markPrice(), ticker.indexPrice(), ticker.bidPrice(), ticker.askPrice(),
                    null, null, null, null, null, null, null, null, ticker.fundingRate(),
                    ticker.nextFundingTime(), ticker.openInterest(),
                    ticker.observedAt(), cursor.completedAt());
            int affected = quoteRepository.writeLatest(quote);
            written += affected;
            if (affected > 0) {
                qualityService.persist(claim.id(), new MarketDataQualityService.MarketDataDimensionRef(
                                dimension.sourceId(), dimension.instrumentId()),
                        qualityService.missingQuoteInputs(ticker.observedAt(), subscription.capabilities(),
                                ticker.markPrice(), ticker.openInterest()));
                cursorService.completeLocked(cursor, null,
                        MarketDataChecksum.sha256(ticker.observedAt() + "|"
                                + MarketDataChecksum.decimal(ticker.lastPrice()) + "|"
                                + nullableDecimal(ticker.openInterest()) + "|"
                                + nullableDecimal(ticker.fundingRate()) + "|"
                                + (ticker.nextFundingTime() == null ? "NONE" : ticker.nextFundingTime())));
                quoteCacheService.refreshAfterCommit(quote);
            }
        }
        return written;
    }

    private String nullableDecimal(java.math.BigDecimal value) {
        return value == null ? "NONE" : MarketDataChecksum.decimal(value);
    }
}
