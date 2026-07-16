package top.egon.mario.investment.marketdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.mario.investment.common.job.InvestmentJobClaim;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.InvestmentJobType;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.common.model.ProductType;
import top.egon.mario.investment.marketdata.ingest.MarketDataCursorService;
import top.egon.mario.investment.marketdata.ingest.MarketDataDimension;
import top.egon.mario.investment.marketdata.ingest.MarketDataDimensionResolver;
import top.egon.mario.investment.marketdata.ingest.MarketDataJobInput;
import top.egon.mario.investment.marketdata.ingest.handler.QuoteRefreshJobHandler;
import top.egon.mario.investment.marketdata.po.InvestmentIngestCursorPo;
import top.egon.mario.investment.marketdata.provider.ContractTickerProvider;
import top.egon.mario.investment.marketdata.provider.ProviderRegistry;
import top.egon.mario.investment.marketdata.provider.model.ExternalContractTicker;
import top.egon.mario.investment.marketdata.quality.MarketDataQualityService;
import top.egon.mario.investment.marketdata.repository.jdbc.ContractQuoteJdbcRepository;
import top.egon.mario.investment.marketdata.subscription.InvestmentMarketSubscriptionRegistry;
import top.egon.mario.investment.marketdata.event.InvestmentMarketDataCommittedEvent;
import top.egon.mario.investment.marketdata.event.MarketDataAfterCommitPublisher;
import top.egon.mario.investment.marketdata.repository.jdbc.model.ContractQuoteWrite;
import top.egon.mario.investment.marketdata.repository.InvestmentDataQualityIssueRepository;
import top.egon.mario.investment.marketdata.service.QuoteCacheService;
import top.egon.mario.investment.marketdata.subscription.MarketSubscription;
import top.egon.mario.investment.marketdata.subscription.RetentionPolicy;
import top.egon.mario.investment.marketdata.subscription.SubscriptionSchedule;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class InvestmentQuoteCacheTests {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final MarketDataAfterCommitPublisher afterCommitPublisher =
            new MarketDataAfterCommitPublisher(eventPublisher);
    private final QuoteCacheService service = new QuoteCacheService(redisTemplate,
            new ObjectMapper().findAndRegisterModules(), afterCommitPublisher);

    InvestmentQuoteCacheTests() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void writesCacheThenPublishesSanitizedEventOnlyAfterCommit() {
        beginTransactionSynchronization();
        service.refreshAfterCommit(quote());

        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
        verify(eventPublisher, never()).publishEvent(any(Object.class));

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        var order = inOrder(valueOperations, eventPublisher);
        order.verify(valueOperations).set(eq("investment:quote:10:20"), any(), eq(Duration.ofMinutes(2)));
        order.verify(eventPublisher).publishEvent(any(InvestmentMarketDataCommittedEvent.class));
    }

    @Test
    void rollbackAndMissingTransactionNeverTouchCacheOrEvents() {
        beginTransactionSynchronization();
        service.refreshAfterCommit(quote());
        TransactionSynchronizationManager.getSynchronizations().forEach(synchronization ->
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
        verify(eventPublisher, never()).publishEvent(any(Object.class));

        clearSynchronization();
        assertThatThrownBy(() -> service.refreshAfterCommit(quote()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active synchronized transaction");
    }

    @Test
    void redisFailureIsBestEffortAndStillPublishesTheSanitizedEvent() {
        beginTransactionSynchronization();
        doThrow(new IllegalStateException("redis unavailable")).when(valueOperations)
                .set(any(), any(), any(Duration.class));
        service.refreshAfterCommit(quote());

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.<Object>argThat(event ->
                event instanceof InvestmentMarketDataCommittedEvent committed
                        && committed.sourceId() == 10 && committed.instrumentId() == 20
                        && committed.dataType().equals("QUOTE") && committed.recordCount() == 1));
    }

    @Test
    void stalePartialQuoteCannotOverwriteACompleteQuoteOrCreateFalseMissingIssues() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        InvestmentMarketSubscriptionRegistry registry = mock(InvestmentMarketSubscriptionRegistry.class);
        ProviderRegistry providerRegistry = mock(ProviderRegistry.class);
        ContractTickerProvider provider = mock(ContractTickerProvider.class);
        when(registry.requireCapability("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                DataCapability.LATEST_TICKER)).thenReturn(subscription(Set.of(DataCapability.LATEST_TICKER,
                DataCapability.OPEN_INTEREST)));
        when(providerRegistry.require("TEST", DataCapability.LATEST_TICKER, ContractTickerProvider.class))
                .thenReturn(provider);
        Instant now = Instant.parse("2026-07-16T00:00:00Z");
        when(provider.tickers(ProductType.USDT_FUTURES, Set.of("BTCUSDT"))).thenReturn(List.of(
                new ExternalContractTicker("TEST", ProductType.USDT_FUTURES, "BTCUSDT", decimal("100"),
                        null, null, null, null, null, now)));
        MarketDataDimensionResolver resolver = mock(MarketDataDimensionResolver.class);
        when(resolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(new MarketDataDimension(10, 20));
        QuoteRepositoryFixture repositoryFixture = quoteRepositoryFixture();
        ContractQuoteJdbcRepository repository = repositoryFixture.repository();
        repository.writeLatest(new ContractQuoteWrite(10, 20, decimal("101"), decimal("100"), decimal("99"),
                decimal("100.5"), decimal("101.5"), null, null, null, null, null, null, null, null,
                null, null, decimal("20"), now.plusSeconds(60), now.plusSeconds(61)));
        MarketDataCursorService cursorService = mock(MarketDataCursorService.class);
        InvestmentIngestCursorPo cursor = new InvestmentIngestCursorPo();
        when(cursorService.lock(new MarketDataDimension(10, 20), "QUOTE", PriceType.NONE, BarInterval.NONE))
                .thenReturn(new MarketDataCursorService.LockedCursor(cursor, now.plusSeconds(120)));
        InvestmentDataQualityIssueRepository issueRepository = mock(InvestmentDataQualityIssueRepository.class);
        MarketDataQualityService qualityService = new MarketDataQualityService(issueRepository, mapper);
        QuoteCacheService cacheService = mock(QuoteCacheService.class);
        QuoteRefreshJobHandler handler = new QuoteRefreshJobHandler(mapper, registry,
                new TransactionTemplate(new DataSourceTransactionManager(repositoryFixture.dataSource())),
                providerRegistry, resolver, repository, cursorService, qualityService, cacheService);
        MarketDataJobInput input = new MarketDataJobInput("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                DataCapability.LATEST_TICKER, PriceType.NONE, BarInterval.NONE, null, null, 1000);
        InvestmentJobClaim claim = new InvestmentJobClaim(1, null, InvestmentJobType.QUOTE_REFRESH,
                mapper.writeValueAsString(input), 1, 5, "worker", "token", now, now.plusSeconds(60));

        handler.execute(claim);

        assertThat(repository.findLatest(10, 20)).hasValueSatisfying(quote -> {
            assertThat(quote.markPrice()).isEqualByComparingTo("100");
            assertThat(quote.openInterest()).isEqualByComparingTo("20");
            assertThat(quote.sourceTime()).isEqualTo(now.plusSeconds(60));
        });
        verify(issueRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(cacheService, never()).refreshAfterCommit(org.mockito.ArgumentMatchers.any());
        verify(cursorService, never()).completeLocked(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void latestTickerAllowsNullOpenInterestWithoutZeroFallbackButAuditsMissingRequiredMark() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        InvestmentMarketSubscriptionRegistry registry = mock(InvestmentMarketSubscriptionRegistry.class);
        when(registry.requireCapability("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                DataCapability.LATEST_TICKER)).thenReturn(subscription(Set.of(DataCapability.LATEST_TICKER)));
        ProviderRegistry providerRegistry = mock(ProviderRegistry.class);
        ContractTickerProvider provider = mock(ContractTickerProvider.class);
        when(providerRegistry.require("TEST", DataCapability.LATEST_TICKER, ContractTickerProvider.class))
                .thenReturn(provider);
        Instant now = Instant.parse("2026-07-16T00:00:00Z");
        when(provider.tickers(ProductType.USDT_FUTURES, Set.of("BTCUSDT"))).thenReturn(List.of(
                new ExternalContractTicker("TEST", ProductType.USDT_FUTURES, "BTCUSDT", decimal("100"),
                        null, null, null, null, null, now)));
        MarketDataDimensionResolver resolver = mock(MarketDataDimensionResolver.class);
        when(resolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(new MarketDataDimension(10, 20));
        ContractQuoteJdbcRepository repository = mock(ContractQuoteJdbcRepository.class);
        when(repository.writeLatest(org.mockito.ArgumentMatchers.any())).thenReturn(1);
        InvestmentDataQualityIssueRepository issueRepository = mock(InvestmentDataQualityIssueRepository.class);
        QuoteCacheService cache = mock(QuoteCacheService.class);
        MarketDataCursorService cursorService = mock(MarketDataCursorService.class);
        Instant completedAt = now.plusSeconds(1);
        when(cursorService.lock(new MarketDataDimension(10, 20), "QUOTE", PriceType.NONE, BarInterval.NONE))
                .thenReturn(new MarketDataCursorService.LockedCursor(new InvestmentIngestCursorPo(), completedAt));
        QuoteRefreshJobHandler handler = new QuoteRefreshJobHandler(mapper, registry,
                new TransactionTemplate(new NoOpTransactionManager()), providerRegistry, resolver, repository,
                cursorService, new MarketDataQualityService(issueRepository, mapper), cache);

        handler.execute(quoteClaim(mapper, DataCapability.LATEST_TICKER, now));

        verify(repository).writeLatest(org.mockito.ArgumentMatchers.argThat(quote ->
                quote.markPrice() == null && quote.openInterest() == null
                        && quote.receivedAt().equals(completedAt)));
        verify(issueRepository).save(org.mockito.ArgumentMatchers.argThat(issue ->
                issue.getIssueCode().equals("MISSING_MARK_PRICE")));
        verify(issueRepository, never()).save(org.mockito.ArgumentMatchers.argThat(issue ->
                issue.getIssueCode().equals("MISSING_OPEN_INTEREST")));
        verify(cache).refreshAfterCommit(org.mockito.ArgumentMatchers.argThat(quote ->
                quote.markPrice() == null && quote.openInterest() == null));
    }

    @Test
    void emptyTickerPageAuditsOnlyCapabilitiesSelectedInCode() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        Set<DataCapability> capabilities = Set.of(DataCapability.LATEST_TICKER, DataCapability.OPEN_INTEREST);
        InvestmentMarketSubscriptionRegistry registry = mock(InvestmentMarketSubscriptionRegistry.class);
        when(registry.requireCapability("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                DataCapability.LATEST_TICKER)).thenReturn(subscription(capabilities));
        ProviderRegistry providerRegistry = mock(ProviderRegistry.class);
        ContractTickerProvider provider = mock(ContractTickerProvider.class);
        when(providerRegistry.require("TEST", DataCapability.LATEST_TICKER, ContractTickerProvider.class))
                .thenReturn(provider);
        when(provider.tickers(ProductType.USDT_FUTURES, Set.of("BTCUSDT"))).thenReturn(List.of());
        MarketDataDimensionResolver resolver = mock(MarketDataDimensionResolver.class);
        when(resolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(new MarketDataDimension(10, 20));
        InvestmentDataQualityIssueRepository issueRepository = mock(InvestmentDataQualityIssueRepository.class);
        MarketDataCursorService cursorService = mock(MarketDataCursorService.class);
        Instant completedAt = Instant.parse("2026-07-16T00:00:01Z");
        when(cursorService.lock(new MarketDataDimension(10, 20), "QUOTE", PriceType.NONE, BarInterval.NONE))
                .thenReturn(new MarketDataCursorService.LockedCursor(new InvestmentIngestCursorPo(), completedAt));
        QuoteRefreshJobHandler handler = new QuoteRefreshJobHandler(mapper, registry,
                new TransactionTemplate(new NoOpTransactionManager()), providerRegistry, resolver,
                mock(ContractQuoteJdbcRepository.class), cursorService,
                new MarketDataQualityService(issueRepository, mapper), mock(QuoteCacheService.class));

        handler.execute(quoteClaim(mapper, DataCapability.LATEST_TICKER,
                Instant.parse("2026-07-16T00:00:00Z")));

        var issues = org.mockito.ArgumentCaptor.forClass(
                top.egon.mario.investment.marketdata.po.InvestmentDataQualityIssuePo.class);
        verify(issueRepository, times(2)).save(issues.capture());
        org.assertj.core.api.Assertions.assertThat(issues.getAllValues())
                .extracting(top.egon.mario.investment.marketdata.po.InvestmentDataQualityIssuePo::getIssueCode)
                .containsExactly("MISSING_MARK_PRICE", "MISSING_OPEN_INTEREST");
    }

    private QuoteRepositoryFixture quoteRepositoryFixture() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:quote_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        jdbcTemplate.getJdbcTemplate().execute("""
                create table investment_contract_quote_latest (
                    source_id bigint not null,
                    instrument_id bigint not null,
                    last_price numeric(38, 18) not null,
                    mark_price numeric(38, 18),
                    index_price numeric(38, 18),
                    bid_price numeric(38, 18),
                    ask_price numeric(38, 18),
                    bid_quantity numeric(38, 18),
                    ask_quantity numeric(38, 18),
                    open_24h numeric(38, 18),
                    high_24h numeric(38, 18),
                    low_24h numeric(38, 18),
                    base_volume_24h numeric(38, 18),
                    quote_volume_24h numeric(38, 18),
                    change_24h numeric(38, 18),
                    funding_rate numeric(24, 12),
                    next_funding_time timestamp with time zone,
                    open_interest numeric(38, 18),
                    source_time timestamp with time zone not null,
                    received_at timestamp with time zone not null,
                    version bigint not null default 0,
                    primary key (source_id, instrument_id)
                )
                """);
        return new QuoteRepositoryFixture(new ContractQuoteJdbcRepository(jdbcTemplate, dataSource), dataSource);
    }

    private void beginTransactionSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    private ContractQuoteWrite quote() {
        Instant now = Instant.parse("2026-07-16T00:00:00Z");
        return new ContractQuoteWrite(10, 20, decimal("100"), decimal("99"), decimal("98"),
                decimal("99.5"), decimal("100.5"), null, null, null, null, null, null, null, null,
                null, null, decimal("10"), now, now.plusMillis(1));
    }

    private InvestmentJobClaim quoteClaim(ObjectMapper mapper, DataCapability capability, Instant now)
            throws Exception {
        MarketDataJobInput input = new MarketDataJobInput("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                capability, PriceType.NONE, BarInterval.NONE, null, null, 1000);
        return new InvestmentJobClaim(1, null, InvestmentJobType.QUOTE_REFRESH,
                mapper.writeValueAsString(input), 1, 5, "worker", "token", now, now.plusSeconds(60));
    }

    private MarketSubscription subscription(Set<DataCapability> capabilities) {
        Map<DataCapability, Duration> refresh = capabilities.stream().collect(java.util.stream.Collectors.toMap(
                capability -> capability, capability -> Duration.ofMinutes(1)));
        return new MarketSubscription("TEST", ProductType.USDT_FUTURES, "BTCUSDT", Set.of(), Set.of(),
                capabilities, new SubscriptionSchedule(refresh, Map.of()),
                new RetentionPolicy(Set.of(), Map.of()));
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private static class NoOpTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }

    private record QuoteRepositoryFixture(ContractQuoteJdbcRepository repository, JdbcDataSource dataSource) {
    }
}
