package top.egon.mario.investment.marketdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.h2.jdbcx.JdbcDataSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.job.InvestmentJobClaim;
import top.egon.mario.investment.common.job.InvestmentJobHandlerResult;
import top.egon.mario.investment.common.job.InvestmentJobNonRetryableException;
import top.egon.mario.investment.common.job.InvestmentJobRetryableException;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.ContractType;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.InvestmentJobType;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.common.model.ProductType;
import top.egon.mario.investment.marketdata.ingest.AbstractMarketDataJobHandler;
import top.egon.mario.investment.marketdata.ingest.MarketDataDimensionResolver;
import top.egon.mario.investment.marketdata.ingest.MarketDataDimension;
import top.egon.mario.investment.marketdata.ingest.MarketDataChecksum;
import top.egon.mario.investment.marketdata.ingest.MarketDataCursorService;
import top.egon.mario.investment.marketdata.ingest.MarketDataJobInput;
import top.egon.mario.investment.marketdata.ingest.handler.BarBackfillJobHandler;
import top.egon.mario.investment.marketdata.ingest.handler.ContractMetadataSyncJobHandler;
import top.egon.mario.investment.marketdata.ingest.handler.CandleSyncJobHandler;
import top.egon.mario.investment.marketdata.ingest.handler.FundingRateSyncJobHandler;
import top.egon.mario.investment.marketdata.ingest.handler.PositionTierSyncJobHandler;
import top.egon.mario.investment.marketdata.event.InvestmentMarketDataCommittedEvent;
import top.egon.mario.investment.marketdata.event.MarketDataAfterCommitPublisher;
import top.egon.mario.investment.marketdata.po.InvestmentDataSourcePo;
import top.egon.mario.investment.marketdata.po.InvestmentContractSpecPo;
import top.egon.mario.investment.marketdata.po.InvestmentIngestCursorPo;
import top.egon.mario.investment.marketdata.po.InvestmentInstrumentPo;
import top.egon.mario.investment.marketdata.po.InvestmentInstrumentSourcePo;
import top.egon.mario.investment.marketdata.po.InvestmentPositionTierPo;
import top.egon.mario.investment.marketdata.provider.ContractMetadataProvider;
import top.egon.mario.investment.marketdata.provider.ContractCandleProvider;
import top.egon.mario.investment.marketdata.provider.FundingRateProvider;
import top.egon.mario.investment.marketdata.provider.PositionTierProvider;
import top.egon.mario.investment.marketdata.provider.MarketDataProviderException;
import top.egon.mario.investment.marketdata.provider.ProviderErrorCategory;
import top.egon.mario.investment.marketdata.provider.ProviderRegistry;
import top.egon.mario.investment.marketdata.provider.model.ExternalContract;
import top.egon.mario.investment.marketdata.provider.model.CandleQuery;
import top.egon.mario.investment.marketdata.provider.model.ExternalCandle;
import top.egon.mario.investment.marketdata.provider.model.ExternalFundingRate;
import top.egon.mario.investment.marketdata.provider.model.FundingRateQuery;
import top.egon.mario.investment.marketdata.provider.model.ExternalPositionTier;
import top.egon.mario.investment.marketdata.quality.MarketDataQualityService;
import top.egon.mario.investment.marketdata.repository.InvestmentDataSourceRepository;
import top.egon.mario.investment.marketdata.repository.InvestmentContractSpecRepository;
import top.egon.mario.investment.marketdata.repository.InvestmentIngestCursorRepository;
import top.egon.mario.investment.marketdata.repository.InvestmentInstrumentRepository;
import top.egon.mario.investment.marketdata.repository.InvestmentInstrumentSourceRepository;
import top.egon.mario.investment.marketdata.repository.InvestmentPositionTierRepository;
import top.egon.mario.investment.marketdata.repository.InvestmentDataQualityIssueRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.FundingRateJdbcRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.MarketBarJdbcRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.model.RevisionBatchResult;
import top.egon.mario.investment.marketdata.repository.jdbc.model.FundingRateWrite;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketDataWriteContext;
import top.egon.mario.investment.marketdata.subscription.InvestmentMarketSubscriptionRegistry;
import top.egon.mario.investment.marketdata.subscription.MarketSubscription;
import top.egon.mario.investment.marketdata.subscription.RetentionPolicy;
import top.egon.mario.investment.marketdata.subscription.SubscriptionSchedule;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.LinkedHashMap;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvestmentMarketDataIngestTests {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final InvestmentMarketSubscriptionRegistry subscriptionRegistry =
            mock(InvestmentMarketSubscriptionRegistry.class);

    @Test
    void fetchesOutsideTransactionsAndCommitsEachNormalizedPageSeparately() throws Exception {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        Instant start = Instant.parse("2026-07-16T00:00:00Z");
        PagedTestHandler handler = new PagedTestHandler(objectMapper, subscriptionRegistry,
                new TransactionTemplate(transactionManager), page -> page.startInclusive().equals(start)
                ? List.of("a", "b") : List.of("c"));

        InvestmentJobHandlerResult result = handler.execute(claim(InvestmentJobType.FUNDING_RATE_INCREMENTAL,
                fundingInput(start, start.plusSeconds(10), 2)));

        assertThat(handler.fetchHadTransaction).isFalse();
        assertThat(handler.persistTransactionStates).containsExactly(true, true);
        assertThat(transactionManager.commits).isEqualTo(2);
        assertThat(result.resultJson()).isEqualTo("{\"fetched\":3,\"written\":3}");
        verify(subscriptionRegistry).requireCapability("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                DataCapability.FUNDING_RATE);
    }

    @Test
    void retriesTransientProviderFailuresAndClassifiesNonCandleInvalidDataWithoutFakeBarAudit() throws Exception {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        AtomicInteger attempts = new AtomicInteger();
        TestHandler retrying = new TestHandler(objectMapper, subscriptionRegistry,
                new TransactionTemplate(transactionManager), List.of("ok")) {
            @Override
            protected List<String> fetch(MarketDataJobInput input) {
                fetchHadTransaction = TransactionSynchronizationManager.isActualTransactionActive();
                if (attempts.incrementAndGet() < 3) {
                    throw new MarketDataProviderException("TEST", ProviderErrorCategory.RETRYABLE, "timeout");
                }
                return List.of("ok");
            }
        };

        retrying.execute(claim(input(1000)));
        assertThat(attempts).hasValue(3);

        MarketDataDimensionResolver invalidDimensionResolver = mock(MarketDataDimensionResolver.class);
        MarketDataQualityService invalidQualityService = mock(MarketDataQualityService.class);
        when(invalidDimensionResolver.resolve(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new MarketDataDimension(10, 20));
        when(invalidQualityService.invalidProviderData(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenCallRealMethod();
        TestHandler invalid = new TestHandler(objectMapper, subscriptionRegistry,
                new TransactionTemplate(transactionManager), invalidDimensionResolver, invalidQualityService,
                List.of()) {
            @Override
            protected List<String> fetch(MarketDataJobInput input) {
                throw new NullPointerException("required ticker field");
            }
        };
        assertThatThrownBy(() -> invalid.execute(claim(input(1000))))
                .isInstanceOf(InvestmentJobNonRetryableException.class)
                .hasMessageContaining("invalid normalized data");
        verify(invalidQualityService, never()).persist(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void candleProviderNormalizationFailureCreatesOnlyACandleQualityAudit() throws Exception {
        Instant start = Instant.parse("2026-07-16T00:00:00Z");
        ProviderRegistry providerRegistry = mock(ProviderRegistry.class);
        ContractCandleProvider provider = mock(ContractCandleProvider.class);
        when(providerRegistry.require("TEST", DataCapability.MARKET_CANDLE, ContractCandleProvider.class))
                .thenReturn(provider);
        when(provider.candles(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalArgumentException("invalid OHLC"));
        MarketDataDimensionResolver resolver = mock(MarketDataDimensionResolver.class);
        when(resolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(new MarketDataDimension(10, 20));
        MarketDataQualityService qualityService = mock(MarketDataQualityService.class);
        when(qualityService.invalidProviderData(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenCallRealMethod();
        CandleSyncJobHandler handler = new CandleSyncJobHandler(objectMapper, subscriptionRegistry,
                new TransactionTemplate(new RecordingTransactionManager()), providerRegistry, resolver,
                mock(MarketBarJdbcRepository.class), qualityService,
                mock(MarketDataAfterCommitPublisher.class), Clock.fixed(start, ZoneOffset.UTC));

        assertThatThrownBy(() -> handler.execute(claim(InvestmentJobType.BAR_INCREMENTAL,
                candleInput(start, start.plusSeconds(60), 10))))
                .isInstanceOf(InvestmentJobNonRetryableException.class)
                .extracting(exception -> ((InvestmentJobNonRetryableException) exception).errorCode())
                .isEqualTo("MARKET_PROVIDER_INVALID_DATA");
        verify(qualityService).persist(org.mockito.ArgumentMatchers.eq(11L),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.argThat(findings ->
                        findings.size() == 1 && findings.getFirst().code().name().equals("OHLC_INVALID")));
    }

    @Test
    void boundsLegacyScheduledMinuteBackfillsButPreservesManualRanges() throws Exception {
        Instant end = Instant.parse("2026-07-16T00:00:00Z");
        Instant start = end.minus(Duration.ofDays(30));
        ProviderRegistry providerRegistry = mock(ProviderRegistry.class);
        ContractCandleProvider provider = mock(ContractCandleProvider.class);
        when(providerRegistry.require("TEST", DataCapability.MARKET_CANDLE, ContractCandleProvider.class))
                .thenReturn(provider);
        when(provider.candles(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        BarBackfillJobHandler handler = new BarBackfillJobHandler(objectMapper, subscriptionRegistry,
                new TransactionTemplate(new RecordingTransactionManager()), providerRegistry,
                mock(MarketDataDimensionResolver.class), mock(MarketBarJdbcRepository.class),
                mock(MarketDataQualityService.class), mock(MarketDataAfterCommitPublisher.class),
                Clock.fixed(end.plusSeconds(60), ZoneOffset.UTC));

        MarketDataJobInput scheduled = new MarketDataJobInput("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                DataCapability.MARKET_CANDLE, PriceType.MARKET, BarInterval.M1, start, end, 100);
        MarketDataJobInput manual = new MarketDataJobInput("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                DataCapability.MARKET_CANDLE, PriceType.MARKET, BarInterval.M1, start, end, 100, "MANUAL");
        handler.execute(claim(InvestmentJobType.BAR_BACKFILL, scheduled));
        handler.execute(claim(InvestmentJobType.BAR_BACKFILL, manual));

        ArgumentCaptor<CandleQuery> queries = ArgumentCaptor.forClass(CandleQuery.class);
        verify(provider, times(2)).candles(queries.capture());
        assertThat(queries.getAllValues().getFirst().startInclusive())
                .isEqualTo(end.minus(Duration.ofDays(1)));
        assertThat(queries.getAllValues().get(1).startInclusive()).isEqualTo(start);
        assertThat(queries.getAllValues()).allSatisfy(query ->
                assertThat(query.endExclusive()).isEqualTo(end));
    }

    @Test
    void retriesOnlyTheFailingProviderPageAndStopsImmediatelyForPermanentFailures() throws Exception {
        Instant start = Instant.parse("2026-07-16T00:00:00Z");
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        AtomicInteger secondPageAttempts = new AtomicInteger();
        PagedTestHandler transientHandler = new PagedTestHandler(objectMapper, subscriptionRegistry,
                new TransactionTemplate(transactionManager), page -> {
            if (page.startInclusive().equals(start)) {
                return List.of("a", "b");
            }
            if (secondPageAttempts.incrementAndGet() < 3) {
                throw new MarketDataProviderException("TEST", ProviderErrorCategory.RETRYABLE, "timeout");
            }
            return List.of("c");
        });

        transientHandler.execute(claim(InvestmentJobType.FUNDING_RATE_INCREMENTAL,
                fundingInput(start, start.plusSeconds(10), 2)));
        assertThat(secondPageAttempts).hasValue(3);
        assertThat(transactionManager.commits).isEqualTo(2);

        AtomicInteger permanentAttempts = new AtomicInteger();
        PagedTestHandler permanent = new PagedTestHandler(objectMapper, subscriptionRegistry,
                new TransactionTemplate(new RecordingTransactionManager()), page -> {
            if (page.startInclusive().equals(start)) {
                return List.of("a", "b");
            }
            permanentAttempts.incrementAndGet();
            throw new MarketDataProviderException("TEST", ProviderErrorCategory.NON_RETRYABLE, "bad request");
        });
        assertThatThrownBy(() -> permanent.execute(claim(InvestmentJobType.FUNDING_RATE_INCREMENTAL,
                fundingInput(start, start.plusSeconds(10), 2))))
                .isInstanceOf(InvestmentJobNonRetryableException.class);
        assertThat(permanentAttempts).hasValue(1);
    }

    @Test
    void candleHandlerUsesProviderPagesAndAuditsCrossPageGaps() throws Exception {
        Instant start = Instant.parse("2026-07-16T00:00:00Z");
        ProviderRegistry providerRegistry = mock(ProviderRegistry.class);
        ContractCandleProvider provider = mock(ContractCandleProvider.class);
        when(providerRegistry.require("TEST", DataCapability.MARKET_CANDLE, ContractCandleProvider.class))
                .thenReturn(provider);
        when(provider.candles(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            CandleQuery query = invocation.getArgument(0);
            if (query.startInclusive().equals(start)) {
                return List.of(candle(start), candle(start.plusSeconds(60)));
            }
            if (query.startInclusive().equals(start.plusSeconds(120))) {
                return List.of(candle(start.plusSeconds(180)), candle(start.plusSeconds(240)));
            }
            return List.of();
        });
        MarketDataDimensionResolver dimensionResolver = mock(MarketDataDimensionResolver.class);
        when(dimensionResolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(new MarketDataDimension(10, 20));
        MarketBarJdbcRepository barRepository = mock(MarketBarJdbcRepository.class);
        when(barRepository.writeIntradayBatch(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList())).thenReturn(new RevisionBatchResult(2, 0, 0, 1),
                new RevisionBatchResult(1, 0, 1, 1));
        InvestmentDataQualityIssueRepository issueRepository = mock(InvestmentDataQualityIssueRepository.class);
        MarketDataQualityService qualityService = new MarketDataQualityService(issueRepository, objectMapper);
        CandleSyncJobHandler handler = new CandleSyncJobHandler(objectMapper, subscriptionRegistry,
                new TransactionTemplate(new RecordingTransactionManager()), providerRegistry, dimensionResolver,
                barRepository, qualityService, mock(MarketDataAfterCommitPublisher.class),
                Clock.fixed(start.plusSeconds(300), ZoneOffset.UTC));
        MarketDataJobInput input = new MarketDataJobInput("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                DataCapability.MARKET_CANDLE, PriceType.MARKET, BarInterval.M1, start,
                start.plusSeconds(300), 2);

        handler.execute(claim(InvestmentJobType.BAR_INCREMENTAL, input));

        ArgumentCaptor<CandleQuery> queries = ArgumentCaptor.forClass(CandleQuery.class);
        verify(provider, times(2)).candles(queries.capture());
        assertThat(queries.getAllValues()).extracting(CandleQuery::startInclusive)
                .containsExactly(start, start.plusSeconds(120));
        verify(barRepository, times(2)).writeIntradayBatch(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList());
        ArgumentCaptor<top.egon.mario.investment.marketdata.po.InvestmentDataQualityIssuePo> issue =
                ArgumentCaptor.forClass(top.egon.mario.investment.marketdata.po.InvestmentDataQualityIssuePo.class);
        verify(issueRepository).save(issue.capture());
        assertThat(issue.getAllValues()).extracting(
                top.egon.mario.investment.marketdata.po.InvestmentDataQualityIssuePo::getIssueCode)
                .containsExactly("GAP");
    }

    @Test
    void candleHandlerAuditsDuplicatesWithinOneValidProviderPage() throws Exception {
        Instant start = Instant.parse("2026-07-16T00:00:00Z");
        ProviderRegistry providerRegistry = mock(ProviderRegistry.class);
        ContractCandleProvider provider = mock(ContractCandleProvider.class);
        when(providerRegistry.require("TEST", DataCapability.MARKET_CANDLE, ContractCandleProvider.class))
                .thenReturn(provider);
        when(provider.candles(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(candle(start), candle(start), candle(start.plusSeconds(60))));
        MarketDataDimensionResolver resolver = mock(MarketDataDimensionResolver.class);
        when(resolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(new MarketDataDimension(10, 20));
        MarketBarJdbcRepository repository = mock(MarketBarJdbcRepository.class);
        when(repository.writeIntradayBatch(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList())).thenReturn(new RevisionBatchResult(2, 0, 0, 1));
        InvestmentDataQualityIssueRepository issueRepository = mock(InvestmentDataQualityIssueRepository.class);
        CandleSyncJobHandler handler = new CandleSyncJobHandler(objectMapper, subscriptionRegistry,
                new TransactionTemplate(new RecordingTransactionManager()), providerRegistry, resolver,
                repository, new MarketDataQualityService(issueRepository, objectMapper),
                mock(MarketDataAfterCommitPublisher.class), Clock.fixed(start.plusSeconds(120), ZoneOffset.UTC));

        handler.execute(claim(InvestmentJobType.BAR_INCREMENTAL,
                candleInput(start, start.plusSeconds(180), 4)));

        verify(repository).writeIntradayBatch(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.argThat(values -> values.size() == 2));
        verify(issueRepository).save(org.mockito.ArgumentMatchers.argThat(issue ->
                issue.getIssueCode().equals("DUPLICATE")));
    }

    @Test
    void candleHandlerRejectsBothHalfOpenRangeBoundsBeforePersistence() throws Exception {
        Instant start = Instant.parse("2026-07-16T00:00:00Z");
        for (ExternalCandle outside : List.of(candle(start.minusSeconds(60)), candle(start.plusSeconds(120)))) {
            ProviderRegistry providerRegistry = mock(ProviderRegistry.class);
            ContractCandleProvider provider = mock(ContractCandleProvider.class);
            when(providerRegistry.require("TEST", DataCapability.MARKET_CANDLE, ContractCandleProvider.class))
                    .thenReturn(provider);
            when(provider.candles(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(outside));
            MarketBarJdbcRepository repository = mock(MarketBarJdbcRepository.class);
            CandleSyncJobHandler handler = new CandleSyncJobHandler(objectMapper, subscriptionRegistry,
                    new TransactionTemplate(new RecordingTransactionManager()), providerRegistry,
                    mock(MarketDataDimensionResolver.class), repository, mock(MarketDataQualityService.class),
                    mock(MarketDataAfterCommitPublisher.class), Clock.fixed(start, ZoneOffset.UTC));

            assertThatThrownBy(() -> handler.execute(claim(InvestmentJobType.BAR_INCREMENTAL,
                    candleInput(start, start.plusSeconds(120), 10))))
                    .isInstanceOf(InvestmentJobNonRetryableException.class)
                    .extracting(exception -> ((InvestmentJobNonRetryableException) exception).errorCode())
                    .isEqualTo("MARKET_PROVIDER_DIMENSION_MISMATCH");
            verify(repository, never()).writeIntradayBatch(org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.anyList());
        }
    }

    @Test
    void candleTimingViolationsCommitAuditThenFailWithoutCallingBarRepository() throws Exception {
        Instant start = Instant.parse("2026-07-16T00:00:00Z");
        ExternalCandle misaligned = candle(start.plusSeconds(30));
        ExternalCandle badClose = new ExternalCandle("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                PriceType.MARKET, BarInterval.M1, start, start.plusSeconds(30), decimal("100"), decimal("101"),
                decimal("99"), decimal("100"), decimal("1"), decimal("100"), true, start.plusSeconds(60));
        for (ExternalCandle invalid : List.of(misaligned, badClose)) {
            ProviderRegistry providerRegistry = mock(ProviderRegistry.class);
            ContractCandleProvider provider = mock(ContractCandleProvider.class);
            when(providerRegistry.require("TEST", DataCapability.MARKET_CANDLE, ContractCandleProvider.class))
                    .thenReturn(provider);
            when(provider.candles(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(invalid));
            MarketDataDimensionResolver resolver = mock(MarketDataDimensionResolver.class);
            when(resolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(new MarketDataDimension(10, 20));
            MarketBarJdbcRepository repository = mock(MarketBarJdbcRepository.class);
            InvestmentDataQualityIssueRepository issueRepository = mock(InvestmentDataQualityIssueRepository.class);
            RecordingTransactionManager transactionManager = new RecordingTransactionManager();
            CandleSyncJobHandler handler = new CandleSyncJobHandler(objectMapper, subscriptionRegistry,
                    new TransactionTemplate(transactionManager), providerRegistry, resolver, repository,
                    new MarketDataQualityService(issueRepository, objectMapper),
                    mock(MarketDataAfterCommitPublisher.class), Clock.fixed(start, ZoneOffset.UTC));

            assertThatThrownBy(() -> handler.execute(claim(InvestmentJobType.BAR_INCREMENTAL,
                    candleInput(start, start.plusSeconds(120), 10))))
                    .isInstanceOf(InvestmentJobNonRetryableException.class)
                    .extracting(exception -> ((InvestmentJobNonRetryableException) exception).errorCode())
                    .isEqualTo("MARKET_PROVIDER_CANDLE_TIME_INVALID");
            assertThat(transactionManager.commits).isEqualTo(1);
            verify(issueRepository).save(org.mockito.ArgumentMatchers.argThat(issue ->
                    issue.getIssueCode().equals("GAP") && issue.getSeverity().equals("ERROR")));
            verify(repository, never()).writeIntradayBatch(org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.anyList());
        }
    }

    @Test
    void fundingHandlerAdvancesTheHalfOpenProviderRangeUntilAShortPage() throws Exception {
        Instant start = Instant.parse("2026-07-16T00:00:00.123Z");
        ProviderRegistry providerRegistry = mock(ProviderRegistry.class);
        FundingRateProvider provider = mock(FundingRateProvider.class);
        when(providerRegistry.require("TEST", DataCapability.FUNDING_RATE, FundingRateProvider.class))
                .thenReturn(provider);
        when(provider.fundingRates(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            FundingRateQuery query = invocation.getArgument(0);
            return query.startInclusive().equals(start)
                    ? List.of(funding(start), funding(start.plusSeconds(60)))
                    : List.of(funding(start.plusSeconds(120)));
        });
        MarketDataDimensionResolver dimensionResolver = mock(MarketDataDimensionResolver.class);
        when(dimensionResolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(new MarketDataDimension(10, 20));
        FundingRateJdbcRepository repository = mock(FundingRateJdbcRepository.class);
        when(repository.writeBatch(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(new RevisionBatchResult(2, 0, 0, 1), new RevisionBatchResult(1, 0, 0, 1));
        FundingRateSyncJobHandler handler = new FundingRateSyncJobHandler(objectMapper, subscriptionRegistry,
                new TransactionTemplate(new RecordingTransactionManager()), providerRegistry, dimensionResolver,
                repository, mock(MarketDataQualityService.class), mock(MarketDataAfterCommitPublisher.class),
                Clock.fixed(start.plusSeconds(300), ZoneOffset.UTC));
        MarketDataJobInput input = fundingInput(start, start.plusSeconds(300), 2);

        handler.execute(claim(InvestmentJobType.FUNDING_RATE_INCREMENTAL, input));

        ArgumentCaptor<FundingRateQuery> queries = ArgumentCaptor.forClass(FundingRateQuery.class);
        verify(provider, times(2)).fundingRates(queries.capture());
        assertThat(queries.getAllValues().get(1).startInclusive()).isEqualTo(start.plusSeconds(60).plusMillis(1));
        assertThat(queries.getAllValues()).allSatisfy(query -> assertThat(query.endExclusive())
                .isEqualTo(start.plusSeconds(300)));
        ArgumentCaptor<MarketDataWriteContext> contexts = ArgumentCaptor.forClass(MarketDataWriteContext.class);
        verify(repository, times(2)).writeBatch(contexts.capture(), org.mockito.ArgumentMatchers.anyList());
        assertThat(contexts.getAllValues()).extracting(MarketDataWriteContext::nextStartTime)
                .containsExactly(start.plusSeconds(60).plusMillis(1), start.plusSeconds(120).plusMillis(1));
    }

    @Test
    void commitsTheFirstPageWhenTheSecondPageExhaustsAndRollsBackOnlyItsOwnTransaction() throws Exception {
        Instant start = Instant.parse("2026-07-16T00:00:00Z");
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        PagedTestHandler handler = new PagedTestHandler(objectMapper, subscriptionRegistry,
                new TransactionTemplate(transactionManager), page -> page.startInclusive().equals(start)
                ? List.of("a", "b") : List.of("c"));
        handler.failPersistValue = "c";

        assertThatThrownBy(() -> handler.execute(claim(InvestmentJobType.FUNDING_RATE_INCREMENTAL,
                fundingInput(start, start.plusSeconds(10), 2))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("page rollback");
        assertThat(transactionManager.commits).isEqualTo(1);
        assertThat(transactionManager.rollbacks).isEqualTo(1);
    }

    @Test
    void durableReplayDoesNotDuplicateFirstPageRevisionCursorOrFundingEvent() throws Exception {
        Instant start = Instant.parse("2026-07-16T00:00:00.123Z");
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        AtomicInteger failedPageAttempts = new AtomicInteger();
        AtomicBoolean recovered = new AtomicBoolean();
        ProviderRegistry providerRegistry = mock(ProviderRegistry.class);
        FundingRateProvider provider = mock(FundingRateProvider.class);
        when(providerRegistry.require("TEST", DataCapability.FUNDING_RATE, FundingRateProvider.class))
                .thenReturn(provider);
        when(provider.fundingRates(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            FundingRateQuery query = invocation.getArgument(0);
            if (query.startInclusive().equals(start)) {
                return List.of(funding(start), funding(start.plusSeconds(60)));
            }
            if (!recovered.get()) {
                failedPageAttempts.incrementAndGet();
                throw new MarketDataProviderException("TEST", ProviderErrorCategory.RETRYABLE, "timeout");
            }
            return List.of(funding(start.plusSeconds(120)));
        });
        MarketDataDimensionResolver resolver = mock(MarketDataDimensionResolver.class);
        when(resolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(new MarketDataDimension(10, 20));
        FundingRateJdbcRepository repository = mock(FundingRateJdbcRepository.class);
        Map<Instant, String> revisions = new LinkedHashMap<>();
        AtomicInteger unchanged = new AtomicInteger();
        AtomicReference<Instant> cursor = new AtomicReference<>();
        when(repository.writeBatch(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList()))
                .thenAnswer(invocation -> {
                    MarketDataWriteContext context = invocation.getArgument(0);
                    List<FundingRateWrite> writes = invocation.getArgument(1);
                    int inserted = 0;
                    for (FundingRateWrite write : writes) {
                        String previous = revisions.putIfAbsent(write.fundingTime(), write.checksum());
                        if (previous == null) {
                            inserted++;
                        } else if (previous.equals(write.checksum())) {
                            unchanged.incrementAndGet();
                        }
                    }
                    cursor.updateAndGet(current -> current == null || context.nextStartTime().isAfter(current)
                            ? context.nextStartTime() : current);
                    return new RevisionBatchResult(inserted, 0, writes.size() - inserted, 1);
                });
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        FundingRateSyncJobHandler handler = new FundingRateSyncJobHandler(objectMapper, subscriptionRegistry,
                new TransactionTemplate(transactionManager), providerRegistry, resolver, repository,
                mock(MarketDataQualityService.class), new MarketDataAfterCommitPublisher(eventPublisher),
                Clock.fixed(start.plusSeconds(300), ZoneOffset.UTC));
        MarketDataJobInput input = fundingInput(start, start.plusSeconds(300), 2);

        assertThatThrownBy(() -> handler.execute(claim(InvestmentJobType.FUNDING_RATE_INCREMENTAL, input)))
                .isInstanceOf(InvestmentJobRetryableException.class)
                .hasMessageContaining("3 attempts");
        assertThat(failedPageAttempts).hasValue(3);
        assertThat(revisions).hasSize(2);
        assertThat(cursor).hasValue(start.plusSeconds(60).plusMillis(1));
        verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.<Object>argThat(event ->
                event instanceof InvestmentMarketDataCommittedEvent
                        committed && committed.dataType().equals("FUNDING_RATE") && committed.recordCount() == 2));

        recovered.set(true);
        handler.execute(claim(InvestmentJobType.FUNDING_RATE_INCREMENTAL, input));

        assertThat(revisions).hasSize(3);
        assertThat(unchanged).hasValue(2);
        assertThat(cursor).hasValue(start.plusSeconds(120).plusMillis(1));
        verify(eventPublisher, times(2)).publishEvent(org.mockito.ArgumentMatchers.<Object>argThat(event ->
                event instanceof InvestmentMarketDataCommittedEvent
                        committed && committed.dataType().equals("FUNDING_RATE")));
    }

    @Test
    void nonQuoteHandlerEventAndDurableTestSeamRollBackWithAnOuterTransaction() throws Exception {
        Instant now = Instant.parse("2026-07-16T00:00:00Z");
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:funding_rollback_" + System.nanoTime()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        jdbcTemplate.getJdbcTemplate().execute("create table test_funding(funding_time timestamp primary key)");
        jdbcTemplate.getJdbcTemplate().execute("create table test_cursor(next_start timestamp)");
        ProviderRegistry providerRegistry = mock(ProviderRegistry.class);
        FundingRateProvider provider = mock(FundingRateProvider.class);
        when(providerRegistry.require("TEST", DataCapability.FUNDING_RATE, FundingRateProvider.class))
                .thenReturn(provider);
        when(provider.fundingRates(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(funding(now)));
        MarketDataDimensionResolver resolver = mock(MarketDataDimensionResolver.class);
        when(resolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(new MarketDataDimension(10, 20));
        FundingRateJdbcRepository repository = mock(FundingRateJdbcRepository.class);
        when(repository.writeBatch(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList()))
                .thenAnswer(invocation -> {
                    MarketDataWriteContext context = invocation.getArgument(0);
                    jdbcTemplate.getJdbcTemplate().update("insert into test_funding(funding_time) values (?)", now);
                    jdbcTemplate.getJdbcTemplate().update("insert into test_cursor(next_start) values (?)",
                            context.nextStartTime());
                    return new RevisionBatchResult(1, 0, 0, 1);
                });
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        TransactionTemplate transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(
                dataSource));
        FundingRateSyncJobHandler handler = new FundingRateSyncJobHandler(objectMapper, subscriptionRegistry,
                transactionTemplate, providerRegistry, resolver, repository, mock(MarketDataQualityService.class),
                new MarketDataAfterCommitPublisher(eventPublisher), Clock.fixed(now.plusSeconds(1), ZoneOffset.UTC));
        InvestmentJobClaim fundingClaim = claim(InvestmentJobType.FUNDING_RATE_INCREMENTAL,
                fundingInput(now, now.plusSeconds(60), 10));

        transactionTemplate.executeWithoutResult(status -> {
            handler.execute(fundingClaim);
            assertThat(jdbcTemplate.getJdbcTemplate().queryForObject(
                    "select count(*) from test_funding", Integer.class)).isEqualTo(1);
            assertThat(jdbcTemplate.getJdbcTemplate().queryForObject(
                    "select count(*) from test_cursor", Integer.class)).isEqualTo(1);
            status.setRollbackOnly();
        });

        assertThat(jdbcTemplate.getJdbcTemplate().queryForObject(
                "select count(*) from test_funding", Integer.class)).isZero();
        assertThat(jdbcTemplate.getJdbcTemplate().queryForObject(
                "select count(*) from test_cursor", Integer.class)).isZero();
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsEveryIllegalJobPayloadShapeBeforeSubscriptionOrProviderAccess() throws Exception {
        TestHandler handler = new TestHandler(objectMapper, subscriptionRegistry,
                new TransactionTemplate(new RecordingTransactionManager()), List.of("must-not-fetch"));
        Instant start = Instant.parse("2026-07-16T00:00:00Z");
        List<ShapeCase> cases = List.of(
                new ShapeCase(InvestmentJobType.BAR_INCREMENTAL, new MarketDataJobInput("TEST",
                        ProductType.USDT_FUTURES, "BTCUSDT", DataCapability.MARKET_CANDLE, PriceType.MARK,
                        BarInterval.M1, start, start.plusSeconds(60), 10)),
                new ShapeCase(InvestmentJobType.FUNDING_RATE_INCREMENTAL, input(10)),
                new ShapeCase(InvestmentJobType.QUOTE_REFRESH, new MarketDataJobInput("TEST",
                        ProductType.USDT_FUTURES, "BTCUSDT", DataCapability.LATEST_TICKER, PriceType.NONE,
                        BarInterval.NONE, start, start.plusSeconds(60), 10)),
                new ShapeCase(InvestmentJobType.CONTRACT_SYNC, new MarketDataJobInput("TEST",
                        ProductType.USDT_FUTURES, "BTCUSDT", DataCapability.CONTRACT_METADATA, PriceType.MARKET,
                        BarInterval.NONE, null, null, 10)),
                new ShapeCase(InvestmentJobType.POSITION_TIER_SYNC, new MarketDataJobInput("TEST",
                        ProductType.USDT_FUTURES, "BTCUSDT", DataCapability.POSITION_TIER, PriceType.NONE,
                        BarInterval.NONE, start, start.plusSeconds(60), 10)),
                new ShapeCase(InvestmentJobType.DATA_QUALITY_CHECK, new MarketDataJobInput("TEST",
                        ProductType.USDT_FUTURES, "BTCUSDT", DataCapability.CONTRACT_METADATA, PriceType.NONE,
                        BarInterval.M1, null, null, 10)));

        for (ShapeCase shapeCase : cases) {
            assertThatThrownBy(() -> handler.execute(claim(shapeCase.jobType(), shapeCase.input())))
                    .isInstanceOf(InvestmentJobNonRetryableException.class)
                    .extracting(exception -> ((InvestmentJobNonRetryableException) exception).errorCode())
                    .isEqualTo("MARKET_JOB_SHAPE_INVALID");
        }
        assertThat(handler.fetchHadTransaction).isFalse();
    }

    @Test
    void nonRevisionCursorTakesTimeAfterLockAndNeverRegressesFreshnessOrProgress() {
        Instant fallbackNow = Instant.parse("2026-07-16T00:00:00Z");
        Instant persistedFuture = fallbackNow.plusSeconds(120);
        AtomicBoolean locked = new AtomicBoolean();
        InvestmentIngestCursorRepository repository = mock(InvestmentIngestCursorRepository.class);
        InvestmentIngestCursorPo cursor = new InvestmentIngestCursorPo();
        cursor.setNextStartTime(fallbackNow.plusSeconds(60));
        cursor.setLastSuccessTime(persistedFuture);
        cursor.setUpdatedAt(persistedFuture);
        when(repository.findDimensionForUpdate(10L, 20L, "QUOTE", PriceType.NONE, BarInterval.NONE))
                .thenAnswer(invocation -> {
                    locked.set(true);
                    return Optional.of(cursor);
                });
        Clock lockCheckingClock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                assertThat(locked).isTrue();
                return fallbackNow;
            }
        };
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:cursor_" + System.nanoTime());
        MarketDataCursorService service = new MarketDataCursorService(repository,
                new NamedParameterJdbcTemplate(dataSource), lockCheckingClock);

        MarketDataCursorService.LockedCursor fence = service.lock(new MarketDataDimension(10, 20), "QUOTE",
                PriceType.NONE, BarInterval.NONE);
        service.completeLocked(fence, fallbackNow.plusSeconds(30), "first");

        assertThat(fence.completedAt()).isEqualTo(persistedFuture);
        assertThat(cursor.getNextStartTime()).isEqualTo(fallbackNow.plusSeconds(60));
        assertThat(cursor.getLastSuccessTime()).isEqualTo(persistedFuture);
        assertThat(cursor.getUpdatedAt()).isEqualTo(persistedFuture);

        service.completeLocked(fence, fallbackNow.plusSeconds(180), "second");

        assertThat(cursor.getNextStartTime()).isEqualTo(fallbackNow.plusSeconds(180));
        assertThat(cursor.getLastChecksum()).isEqualTo("second");
        assertThat(cursor.getStatus()).isEqualTo("SUCCEEDED");
    }

    @Test
    @SuppressWarnings("unchecked")
    void nonRevisionCursorPrefersPostgresClockTimestampAfterLock() {
        Instant databaseNow = Instant.parse("2026-07-16T00:00:05Z");
        InvestmentIngestCursorRepository repository = mock(InvestmentIngestCursorRepository.class);
        InvestmentIngestCursorPo cursor = new InvestmentIngestCursorPo();
        when(repository.findDimensionForUpdate(10L, 20L, "QUOTE", PriceType.NONE, BarInterval.NONE))
                .thenReturn(Optional.of(cursor));
        NamedParameterJdbcTemplate namedTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(namedTemplate.getJdbcTemplate()).thenReturn(jdbcTemplate);
        when(jdbcTemplate.execute(org.mockito.ArgumentMatchers.<ConnectionCallback<Boolean>>any()))
                .thenReturn(true);
        when(namedTemplate.queryForObject(org.mockito.ArgumentMatchers.eq(
                        "select clock_timestamp() as authoritative_at"),
                org.mockito.ArgumentMatchers.eq(Map.of()), org.mockito.ArgumentMatchers.<RowMapper<Instant>>any()))
                .thenReturn(databaseNow);
        Clock fallbackClock = mock(Clock.class);
        MarketDataCursorService service = new MarketDataCursorService(repository, namedTemplate, fallbackClock);

        MarketDataCursorService.LockedCursor fence = service.lock(new MarketDataDimension(10, 20), "QUOTE",
                PriceType.NONE, BarInterval.NONE);

        assertThat(fence.completedAt()).isEqualTo(databaseNow);
        var order = inOrder(repository, jdbcTemplate, namedTemplate);
        order.verify(repository).findDimensionForUpdate(10L, 20L, "QUOTE", PriceType.NONE, BarInterval.NONE);
        order.verify(jdbcTemplate).execute(org.mockito.ArgumentMatchers.<ConnectionCallback<Boolean>>any());
        order.verify(namedTemplate).queryForObject(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.<Map<String, ?>>any(),
                org.mockito.ArgumentMatchers.<RowMapper<Instant>>any());
        verify(fallbackClock, never()).instant();
    }

    @Test
    void cursorSeedIsIdempotentAndDoesNotOverwriteExistingStateOnH2() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:cursor_seed_" + System.nanoTime()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        jdbcTemplate.getJdbcTemplate().execute("""
                create table investment_ingest_cursor (
                    id bigint generated by default as identity primary key,
                    source_id bigint not null,
                    instrument_id bigint not null,
                    data_type varchar(64) not null,
                    price_type varchar(32) not null,
                    interval_code varchar(32) not null,
                    status varchar(32) not null,
                    updated_at timestamp with time zone not null,
                    version bigint not null,
                    unique (source_id, instrument_id, data_type, price_type, interval_code)
                )
                """);
        Instant fallbackSeedTime = Instant.parse("2026-07-16T00:00:00Z");
        Clock fallbackClock = Clock.fixed(fallbackSeedTime, ZoneOffset.UTC);
        MarketDataCursorService service = new MarketDataCursorService(
                mock(InvestmentIngestCursorRepository.class), jdbcTemplate, fallbackClock);
        MarketDataDimension dimension = new MarketDataDimension(10, 20);

        service.seedIfAbsent(dimension, "CONTRACT_METADATA", PriceType.NONE, BarInterval.NONE);
        Instant seededAt = jdbcTemplate.getJdbcTemplate().queryForObject(
                "select updated_at from investment_ingest_cursor", java.time.OffsetDateTime.class).toInstant();
        assertThat(seededAt).isEqualTo(fallbackSeedTime);
        jdbcTemplate.getJdbcTemplate().update("update investment_ingest_cursor set status = 'SUCCEEDED'");
        service.seedIfAbsent(dimension, "CONTRACT_METADATA", PriceType.NONE, BarInterval.NONE);

        assertThat(jdbcTemplate.getJdbcTemplate().queryForObject(
                "select count(*) from investment_ingest_cursor", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.getJdbcTemplate().queryForObject(
                "select status from investment_ingest_cursor", String.class)).isEqualTo("SUCCEEDED");
        assertThat(jdbcTemplate.getJdbcTemplate().queryForObject(
                "select updated_at from investment_ingest_cursor", java.time.OffsetDateTime.class).toInstant())
                .isEqualTo(seededAt);
    }

    @Test
    void cursorSeedUsesPostgresClockAndAtomicConflictNoOp() {
        NamedParameterJdbcTemplate namedTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(namedTemplate.getJdbcTemplate()).thenReturn(jdbcTemplate);
        when(jdbcTemplate.execute(org.mockito.ArgumentMatchers.<ConnectionCallback<Boolean>>any()))
                .thenReturn(true);
        Clock fallbackClock = mock(Clock.class);
        MarketDataCursorService service = new MarketDataCursorService(
                mock(InvestmentIngestCursorRepository.class), namedTemplate, fallbackClock);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);

        service.seedIfAbsent(new MarketDataDimension(10, 20), "CONTRACT_METADATA",
                PriceType.NONE, BarInterval.NONE);

        verify(namedTemplate).update(sql.capture(),
                org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class));
        assertThat(sql.getValue()).contains("clock_timestamp()", "on conflict", "do nothing");
        verify(fallbackClock, never()).instant();
    }

    @Test
    void metadataSyncUsesPlatformFreshnessAndRejectsStaleOrConflictingProviderSnapshots() throws Exception {
        ProviderRegistry providerRegistry = mock(ProviderRegistry.class);
        ContractMetadataProvider provider = mock(ContractMetadataProvider.class);
        InvestmentDataSourceRepository sourceRepository = mock(InvestmentDataSourceRepository.class);
        InvestmentInstrumentRepository instrumentRepository = mock(InvestmentInstrumentRepository.class);
        InvestmentInstrumentSourceRepository mappingRepository = mock(InvestmentInstrumentSourceRepository.class);
        InvestmentContractSpecRepository specRepository = mock(InvestmentContractSpecRepository.class);
        MarketDataCursorService cursorService = mock(MarketDataCursorService.class);
        Instant platformNow = Instant.parse("2026-07-16T00:00:00Z");
        Instant providerNow = Instant.parse("2030-01-01T00:00:00Z");
        MarketSubscription subscription = new MarketSubscription("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                Set.of(), Set.of(), Set.of(DataCapability.CONTRACT_METADATA),
                new SubscriptionSchedule(Map.of(DataCapability.CONTRACT_METADATA, Duration.ofHours(1)), Map.of()),
                new RetentionPolicy(Set.of(), Map.of()));
        when(subscriptionRegistry.requireCapability("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                DataCapability.CONTRACT_METADATA)).thenReturn(subscription);
        when(providerRegistry.require("TEST", DataCapability.CONTRACT_METADATA, ContractMetadataProvider.class))
                .thenReturn(provider);
        when(provider.contracts(ProductType.USDT_FUTURES, Set.of("BTCUSDT")))
                .thenReturn(List.of(contract(providerNow, "100")),
                        List.of(contract(providerNow.plusSeconds(60), "100")),
                        List.of(contract(providerNow.minusSeconds(1), "125")),
                        List.of(contract(providerNow.plusSeconds(60), "125")),
                        List.of(contract(providerNow.plusSeconds(120), "125")));
        InvestmentDataSourcePo source = new InvestmentDataSourcePo();
        source.setId(10L);
        source.setVenueId(20L);
        when(sourceRepository.findByCodeAndDeletedFalse("TEST")).thenReturn(Optional.of(source));
        AtomicReference<InvestmentInstrumentPo> instrumentState = new AtomicReference<>();
        when(instrumentRepository.findByVenueIdAndProductTypeAndSymbolAndDeletedFalse(
                20L, ProductType.USDT_FUTURES, "BTCUSDT"))
                .thenAnswer(invocation -> Optional.ofNullable(instrumentState.get()));
        when(instrumentRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            InvestmentInstrumentPo instrument = invocation.getArgument(0);
            instrument.setId(30L);
            instrumentState.set(instrument);
            return instrument;
        });
        when(mappingRepository.findByInstrumentIdAndSourceIdAndDeletedFalse(30L, 10L))
                .thenReturn(Optional.empty());
        AtomicReference<InvestmentInstrumentSourcePo> mappingState = new AtomicReference<>();
        when(mappingRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            InvestmentInstrumentSourcePo mapping = invocation.getArgument(0);
            mappingState.set(mapping);
            return mapping;
        });
        InvestmentIngestCursorPo metadataCursor = new InvestmentIngestCursorPo();
        metadataCursor.setUpdatedAt(platformNow.minusSeconds(1));
        AtomicReference<InvestmentContractSpecPo> specState = new AtomicReference<>();
        when(specRepository.findById(30L)).thenAnswer(invocation -> Optional.ofNullable(specState.get()));
        when(specRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            InvestmentContractSpecPo spec = invocation.getArgument(0);
            specState.set(spec);
            return spec;
        });
        AtomicInteger lockCount = new AtomicInteger();
        when(cursorService.lock(new MarketDataDimension(10, 30), "CONTRACT_METADATA", PriceType.NONE,
                BarInterval.NONE)).thenAnswer(invocation -> new MarketDataCursorService.LockedCursor(metadataCursor,
                platformNow.plusSeconds(10L * lockCount.incrementAndGet())));
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        ContractMetadataSyncJobHandler handler = new ContractMetadataSyncJobHandler(objectMapper,
                subscriptionRegistry, new TransactionTemplate(new RecordingTransactionManager()),
                mock(MarketDataDimensionResolver.class), mock(MarketDataQualityService.class), providerRegistry,
                sourceRepository, instrumentRepository, mappingRepository, specRepository, cursorService,
                new MarketDataAfterCommitPublisher(eventPublisher));
        MarketDataJobInput metadataInput = new MarketDataJobInput("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                DataCapability.CONTRACT_METADATA, PriceType.NONE, BarInterval.NONE, null, null, 1000);

        InvestmentJobClaim metadataClaim = new InvestmentJobClaim(12, null,
                InvestmentJobType.CONTRACT_SYNC, objectMapper.writeValueAsString(metadataInput), 1, 5,
                "worker", "token", platformNow, platformNow.plusSeconds(60));

        InvestmentJobHandlerResult created = handler.execute(metadataClaim);

        assertThat(created.resultJson()).isEqualTo(
                "{\"fetched\":1,\"identityRecords\":1,\"contractSpecsWritten\":1}");
        assertCompleteSpec(specState.get(), platformNow.plusSeconds(10), providerNow, 1, decimal("100"));
        assertThat(mappingState.get().getLastSyncedAt()).isEqualTo(platformNow.plusSeconds(10));
        assertThat(specState.get().getRawMetadataJson()).doesNotContain("observedAt", providerNow.toString());
        verify(specRepository).save(org.mockito.ArgumentMatchers.any(InvestmentContractSpecPo.class));
        verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.any(
                InvestmentMarketDataCommittedEvent.class));

        InvestmentJobHandlerResult unchanged = handler.execute(metadataClaim);

        assertThat(unchanged.resultJson()).contains("\"contractSpecsWritten\":0");
        assertCompleteSpec(specState.get(), platformNow.plusSeconds(20), providerNow.plusSeconds(60),
                1, decimal("100"));
        assertThat(mappingState.get().getLastSyncedAt()).isEqualTo(platformNow.plusSeconds(20));
        verify(specRepository, times(2)).save(org.mockito.ArgumentMatchers.any(InvestmentContractSpecPo.class));
        verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.any(
                InvestmentMarketDataCommittedEvent.class));
        verify(cursorService, times(2)).completeLocked(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyString());

        assertThatThrownBy(() -> handler.execute(metadataClaim))
                .isInstanceOf(InvestmentJobNonRetryableException.class)
                .extracting(exception -> ((InvestmentJobNonRetryableException) exception).errorCode())
                .isEqualTo("MARKET_CONTRACT_METADATA_STALE");
        assertCompleteSpec(specState.get(), platformNow.plusSeconds(20), providerNow.plusSeconds(60),
                1, decimal("100"));
        assertThat(mappingState.get().getLastSyncedAt()).isEqualTo(platformNow.plusSeconds(20));

        assertThatThrownBy(() -> handler.execute(metadataClaim))
                .isInstanceOf(InvestmentJobNonRetryableException.class)
                .extracting(exception -> ((InvestmentJobNonRetryableException) exception).errorCode())
                .isEqualTo("MARKET_CONTRACT_METADATA_OBSERVED_AT_CONFLICT");
        assertCompleteSpec(specState.get(), platformNow.plusSeconds(20), providerNow.plusSeconds(60),
                1, decimal("100"));
        assertThat(mappingState.get().getLastSyncedAt()).isEqualTo(platformNow.plusSeconds(20));

        InvestmentJobHandlerResult revised = handler.execute(metadataClaim);

        assertThat(revised.resultJson()).contains("\"contractSpecsWritten\":1");
        assertCompleteSpec(specState.get(), platformNow.plusSeconds(50), providerNow.plusSeconds(120),
                2, decimal("125"));
        assertThat(mappingState.get().getLastSyncedAt()).isEqualTo(platformNow.plusSeconds(50));
        verify(specRepository, times(3)).save(org.mockito.ArgumentMatchers.any(InvestmentContractSpecPo.class));
        verify(eventPublisher, times(2)).publishEvent(org.mockito.ArgumentMatchers.any(
                InvestmentMarketDataCommittedEvent.class));
        verify(mappingRepository, times(3)).save(org.mockito.ArgumentMatchers.any());
        verify(cursorService, times(5)).seedIfAbsent(new MarketDataDimension(10, 30),
                "CONTRACT_METADATA", PriceType.NONE, BarInterval.NONE);
        verify(cursorService, times(3)).completeLocked(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void positionTierTreatsProviderResultAsOneSnapshotAndLocksCursorBeforeReadingCurrentHash() throws Exception {
        TierFixture fixture = tierFixture();
        when(fixture.provider.positionTiers(ProductType.USDT_FUTURES, "BTCUSDT"))
                .thenReturn(tiers(fixture.observedAt, "0", "100", "100", "200"));
        ArgumentCaptor<Iterable<InvestmentPositionTierPo>> captor = ArgumentCaptor.forClass(Iterable.class);

        fixture.handler.execute(claim(InvestmentJobType.POSITION_TIER_SYNC, tierInput(1)));

        verify(fixture.tierRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue()).allSatisfy(tier -> {
            assertThat(tier.getIngestedAt()).isEqualTo(fixture.observedAt.plusSeconds(120));
            assertThat(tier.getLastSeenAt()).isEqualTo(fixture.observedAt.plusSeconds(120));
            assertThat(tier.getLastSeenAt()).isAfterOrEqualTo(tier.getObservedAt());
        });
        var order = inOrder(fixture.cursorService, fixture.jdbcTemplate, fixture.tierRepository);
        order.verify(fixture.cursorService).lock(new MarketDataDimension(10, 20), "POSITION_TIER",
                PriceType.NONE, BarInterval.NONE);
        order.verify(fixture.jdbcTemplate).query(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.<Map<String, ?>>any(),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<?>>any());
        order.verify(fixture.tierRepository).saveAll(org.mockito.ArgumentMatchers.any());
        verify(fixture.afterCommitPublisher).publishAfterCommit(org.mockito.ArgumentMatchers.argThat(event ->
                event.dataType().equals("POSITION_TIER") && event.recordCount() == 2));
    }

    @Test
    void positionTierCanonicalScaleUpdatesLastSeenWithoutCreatingAnotherSnapshot() throws Exception {
        TierFixture fixture = tierFixture();
        String hash = tierHash("0", "100", "100", "200");
        fixture.jdbcTemplate.getJdbcTemplate().update("""
                insert into investment_position_tier
                    (source_id, instrument_id, observed_at, tier_level, source_hash, last_seen_at)
                values (?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?)
                """, 10L, 20L, fixture.observedAt, 1, hash, fixture.observedAt,
                10L, 20L, fixture.observedAt, 2, hash, fixture.observedAt);
        when(fixture.provider.positionTiers(ProductType.USDT_FUTURES, "BTCUSDT"))
                .thenReturn(tiers(fixture.observedAt.plusSeconds(60), "0.00", "100.000", "100.0", "200.00"));

        fixture.handler.execute(claim(InvestmentJobType.POSITION_TIER_SYNC, tierInput(1000)));

        verify(fixture.tierRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
        assertThat(fixture.jdbcTemplate.getJdbcTemplate().queryForObject(
                "select count(*) from investment_position_tier", Integer.class)).isEqualTo(2);
        verify(fixture.cursorService).completeLocked(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(hash));
        verify(fixture.afterCommitPublisher, never()).publishAfterCommit(org.mockito.ArgumentMatchers.any());
        assertThat(fixture.jdbcTemplate.getJdbcTemplate().queryForObject(
                "select min(last_seen_at) from investment_position_tier", java.time.OffsetDateTime.class)
                .toInstant()).isEqualTo(fixture.observedAt.plusSeconds(120));
    }

    @Test
    void emptyPositionTierPageAuditsMissingTierWithoutCompletingCursor() throws Exception {
        TierFixture fixture = tierFixture();
        fixture.cursor.setStatus("IDLE");
        fixture.cursor.setLastChecksum("previous");
        fixture.cursor.setLastSuccessTime(fixture.observedAt.minusSeconds(60));
        when(fixture.provider.positionTiers(ProductType.USDT_FUTURES, "BTCUSDT")).thenReturn(List.of());

        InvestmentJobHandlerResult result = fixture.handler.execute(
                claim(InvestmentJobType.POSITION_TIER_SYNC, tierInput(1000)));

        assertThat(result.resultJson()).contains("\"fetched\":0", "\"written\":0");
        verify(fixture.qualityService).persist(org.mockito.ArgumentMatchers.eq(11L),
                org.mockito.ArgumentMatchers.eq(new MarketDataQualityService.MarketDataDimensionRef(10, 20)),
                org.mockito.ArgumentMatchers.argThat(findings -> findings.size() == 1
                        && findings.getFirst().code()
                        == top.egon.mario.investment.marketdata.quality.MarketDataQualityCode.MISSING_POSITION_TIER));
        verify(fixture.cursorService, never()).completeLocked(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(fixture.tierRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
        assertThat(fixture.cursor.getStatus()).isEqualTo("IDLE");
        assertThat(fixture.cursor.getLastChecksum()).isEqualTo("previous");
        assertThat(fixture.cursor.getLastSuccessTime()).isEqualTo(fixture.observedAt.minusSeconds(60));
    }

    @Test
    void positionTierRejectsMixedOrConflictingObservedAtWithoutUniqueKeyCollisions() throws Exception {
        TierFixture fixture = tierFixture();
        List<ExternalPositionTier> mixed = List.of(tier(1, "0", "100", fixture.observedAt),
                tier(2, "100", "200", fixture.observedAt.plusSeconds(1)));
        when(fixture.provider.positionTiers(ProductType.USDT_FUTURES, "BTCUSDT")).thenReturn(mixed);
        assertThatThrownBy(() -> fixture.handler.execute(
                claim(InvestmentJobType.POSITION_TIER_SYNC, tierInput(1000))))
                .isInstanceOf(InvestmentJobNonRetryableException.class)
                .hasMessageContaining("one observedAt");
        verify(fixture.cursorService, never()).lock(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());

        TierFixture conflict = tierFixture();
        String currentHash = tierHash("0", "100", "100", "200");
        conflict.jdbcTemplate.getJdbcTemplate().update("""
                insert into investment_position_tier
                    (source_id, instrument_id, observed_at, tier_level, source_hash, last_seen_at)
                values (?, ?, ?, ?, ?, ?)
                """, 10L, 20L, conflict.observedAt, 1, currentHash, conflict.observedAt);
        when(conflict.provider.positionTiers(ProductType.USDT_FUTURES, "BTCUSDT"))
                .thenReturn(tiers(conflict.observedAt, "0", "110", "110", "200"));
        assertThatThrownBy(() -> conflict.handler.execute(
                claim(InvestmentJobType.POSITION_TIER_SYNC, tierInput(1000))))
                .isInstanceOf(InvestmentJobNonRetryableException.class)
                .hasMessageContaining("already persisted observedAt");
        verify(conflict.tierRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void scaleCanonicalizationIsStableAndZeroSafe() {
        assertThat(MarketDataChecksum.decimal(decimal("1.00"))).isEqualTo("1");
        assertThat(MarketDataChecksum.decimal(decimal("0.000"))).isEqualTo("0");
        assertThat(tierHash("0", "100.00", "100.0", "200.000"))
                .isEqualTo(tierHash("0.000", "100", "100", "200"));
    }

    @Test
    void revokedSubscriptionIsAuditedAndFailsPermanentlyBeforeProviderFetch() throws Exception {
        MarketDataDimensionResolver dimensionResolver = mock(MarketDataDimensionResolver.class);
        MarketDataQualityService qualityService = mock(MarketDataQualityService.class);
        when(subscriptionRegistry.requireCapability("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                DataCapability.LATEST_TICKER)).thenThrow(new InvestmentException(
                InvestmentErrorCode.SUBSCRIPTION_REJECTED, "revoked"));
        when(dimensionResolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(new MarketDataDimension(10, 20));
        when(qualityService.outOfSubscription(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenCallRealMethod();
        TestHandler handler = new TestHandler(objectMapper, subscriptionRegistry,
                new TransactionTemplate(new RecordingTransactionManager()), dimensionResolver, qualityService,
                List.of("must-not-fetch"));

        assertThatThrownBy(() -> handler.execute(claim(input(1000))))
                .isInstanceOf(InvestmentJobNonRetryableException.class)
                .hasMessageContaining("revoked");
        assertThat(handler.fetchHadTransaction).isFalse();
        verify(qualityService).persist(org.mockito.ArgumentMatchers.eq(11L),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void missingProviderCapabilityIsAuditedAndFailsPermanentlyInsteadOfUnexpectedRetry() throws Exception {
        MarketDataDimensionResolver dimensionResolver = mock(MarketDataDimensionResolver.class);
        MarketDataQualityService qualityService = mock(MarketDataQualityService.class);
        when(dimensionResolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(new MarketDataDimension(10, 20));
        when(qualityService.outOfSubscription(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenCallRealMethod();
        TestHandler handler = new TestHandler(objectMapper, subscriptionRegistry,
                new TransactionTemplate(new RecordingTransactionManager()), dimensionResolver, qualityService,
                List.of()) {
            @Override
            protected List<String> fetch(MarketDataJobInput input) {
                throw new InvestmentException(InvestmentErrorCode.CAPABILITY_UNAVAILABLE, "provider missing");
            }
        };

        assertThatThrownBy(() -> handler.execute(claim(input(1000))))
                .isInstanceOf(InvestmentJobNonRetryableException.class)
                .extracting(exception -> ((InvestmentJobNonRetryableException) exception).errorCode())
                .isEqualTo("MARKET_PROVIDER_CAPABILITY_UNAVAILABLE");
        verify(qualityService).persist(org.mockito.ArgumentMatchers.eq(11L),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList());
    }

    private MarketDataJobInput input(int pageSize) {
        return new MarketDataJobInput("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                DataCapability.LATEST_TICKER, PriceType.NONE, BarInterval.NONE, null, null, pageSize);
    }

    private InvestmentJobClaim claim(MarketDataJobInput input) throws Exception {
        return claim(InvestmentJobType.QUOTE_REFRESH, input);
    }

    private InvestmentJobClaim claim(InvestmentJobType jobType, MarketDataJobInput input) throws Exception {
        return new InvestmentJobClaim(11, null, jobType,
                objectMapper.writeValueAsString(input), 1, 5, "worker", "token",
                Instant.parse("2026-07-16T00:00:00Z"), Instant.parse("2026-07-16T00:01:00Z"));
    }

    private MarketDataJobInput fundingInput(Instant start, Instant end, int pageSize) {
        return new MarketDataJobInput("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                DataCapability.FUNDING_RATE, PriceType.NONE, BarInterval.NONE, start, end, pageSize);
    }

    private MarketDataJobInput candleInput(Instant start, Instant end, int pageSize) {
        return new MarketDataJobInput("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                DataCapability.MARKET_CANDLE, PriceType.MARKET, BarInterval.M1, start, end, pageSize);
    }

    private MarketDataJobInput tierInput(int pageSize) {
        return new MarketDataJobInput("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                DataCapability.POSITION_TIER, PriceType.NONE, BarInterval.NONE, null, null, pageSize);
    }

    private ExternalCandle candle(Instant openTime) {
        return new ExternalCandle("TEST", ProductType.USDT_FUTURES, "BTCUSDT", PriceType.MARKET,
                BarInterval.M1, openTime, openTime.plusSeconds(60), decimal("100"), decimal("101"),
                decimal("99"), decimal("100"), decimal("1"), decimal("100"), true,
                openTime.plusSeconds(60));
    }

    private ExternalFundingRate funding(Instant fundingTime) {
        return new ExternalFundingRate("TEST", ProductType.USDT_FUTURES, "BTCUSDT", decimal("0.001"),
                fundingTime, fundingTime.plusSeconds(1));
    }

    private ExternalContract contract(Instant observedAt, String maxLeverage) {
        return new ExternalContract("TEST", ProductType.USDT_FUTURES, "BTCUSDT", ContractType.PERPETUAL,
                "BTC", "USDT", "USDT", 2, 3, decimal("0.01"), decimal("0.001"), decimal("0.1"),
                decimal("0.001"), decimal("5"), decimal("100"), decimal("1000"), decimal("0.0002"),
                decimal("0.0006"), decimal("1"), decimal(maxLeverage), 8, decimal("1.05"),
                decimal("0.95"), observedAt);
    }

    private void assertCompleteSpec(InvestmentContractSpecPo spec, Instant ingestedAt, Instant sourceUpdatedAt,
                                    long revision, BigDecimal maxLeverage) {
        assertThat(spec.getInstrumentId()).isEqualTo(30L);
        assertThat(spec.getSourceId()).isEqualTo(10L);
        assertThat(spec.getPricePrecision()).isEqualTo(2);
        assertThat(spec.getQuantityPrecision()).isEqualTo(3);
        assertThat(spec.getPriceEndStep()).isEqualByComparingTo("0.01");
        assertThat(spec.getQuantityStep()).isEqualByComparingTo("0.001");
        assertThat(spec.getContractMultiplier()).isEqualByComparingTo("0.1");
        assertThat(spec.getMinTradeQuantity()).isEqualByComparingTo("0.001");
        assertThat(spec.getMinTradeNotional()).isEqualByComparingTo("5");
        assertThat(spec.getMaxMarketOrderQuantity()).isEqualByComparingTo("100");
        assertThat(spec.getMaxLimitOrderQuantity()).isEqualByComparingTo("1000");
        assertThat(spec.getMakerFeeRate()).isEqualByComparingTo("0.0002");
        assertThat(spec.getTakerFeeRate()).isEqualByComparingTo("0.0006");
        assertThat(spec.getMinLeverage()).isEqualByComparingTo("1");
        assertThat(spec.getMaxLeverage()).isEqualByComparingTo(maxLeverage);
        assertThat(spec.getFundingIntervalHours()).isEqualTo(8);
        assertThat(spec.getBuyLimitPriceRatio()).isEqualByComparingTo("1.05");
        assertThat(spec.getSellLimitPriceRatio()).isEqualByComparingTo("0.95");
        assertThat(spec.getSourceUpdatedAt()).isEqualTo(sourceUpdatedAt);
        assertThat(spec.getIngestedAt()).isEqualTo(ingestedAt);
        assertThat(spec.getRevision()).isEqualTo(revision);
    }

    private TierFixture tierFixture() {
        Instant observedAt = Instant.parse("2026-07-16T00:00:00Z");
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:tier_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        NamedParameterJdbcTemplate jdbcTemplate = spy(new NamedParameterJdbcTemplate(dataSource));
        jdbcTemplate.getJdbcTemplate().execute("""
                create table investment_position_tier (
                    source_id bigint not null,
                    instrument_id bigint not null,
                    observed_at timestamp with time zone not null,
                    tier_level integer not null,
                    source_hash varchar(64) not null,
                    last_seen_at timestamp with time zone not null
                )
                """);
        ProviderRegistry providerRegistry = mock(ProviderRegistry.class);
        PositionTierProvider provider = mock(PositionTierProvider.class);
        when(providerRegistry.require("TEST", DataCapability.POSITION_TIER, PositionTierProvider.class))
                .thenReturn(provider);
        MarketDataDimensionResolver dimensionResolver = mock(MarketDataDimensionResolver.class);
        when(dimensionResolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(new MarketDataDimension(10, 20));
        InvestmentPositionTierRepository tierRepository = mock(InvestmentPositionTierRepository.class);
        MarketDataCursorService cursorService = mock(MarketDataCursorService.class);
        InvestmentIngestCursorPo cursor = new InvestmentIngestCursorPo();
        when(cursorService.lock(new MarketDataDimension(10, 20), "POSITION_TIER", PriceType.NONE,
                BarInterval.NONE)).thenReturn(new MarketDataCursorService.LockedCursor(cursor,
                observedAt.plusSeconds(120)));
        MarketDataQualityService qualityService = mock(MarketDataQualityService.class);
        when(qualityService.missingContractInputs(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean())).thenCallRealMethod();
        MarketDataAfterCommitPublisher afterCommitPublisher = mock(MarketDataAfterCommitPublisher.class);
        PositionTierSyncJobHandler handler = new PositionTierSyncJobHandler(objectMapper, subscriptionRegistry,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)), providerRegistry,
                dimensionResolver, tierRepository, jdbcTemplate, cursorService,
                qualityService, afterCommitPublisher);
        return new TierFixture(handler, provider, tierRepository, cursorService, jdbcTemplate,
                qualityService, afterCommitPublisher, cursor, observedAt);
    }

    private List<ExternalPositionTier> tiers(Instant observedAt, String firstStart, String firstEnd,
                                             String secondStart, String secondEnd) {
        return List.of(tier(1, firstStart, firstEnd, observedAt),
                tier(2, secondStart, secondEnd, observedAt));
    }

    private ExternalPositionTier tier(int level, String start, String end, Instant observedAt) {
        return new ExternalPositionTier("TEST", ProductType.USDT_FUTURES, "BTCUSDT", level,
                decimal(start), decimal(end), decimal("0.01"), 100 / level, observedAt);
    }

    private String tierHash(String firstStart, String firstEnd, String secondStart, String secondEnd) {
        String first = "1:" + MarketDataChecksum.decimal(decimal(firstStart)) + ":"
                + MarketDataChecksum.decimal(decimal(firstEnd)) + ":0.01:100";
        String second = "2:" + MarketDataChecksum.decimal(decimal(secondStart)) + ":"
                + MarketDataChecksum.decimal(decimal(secondEnd)) + ":0.01:50";
        return MarketDataChecksum.sha256(first + "|" + second);
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private static class TestHandler extends AbstractMarketDataJobHandler<String> {

        private final List<String> values;
        protected boolean fetchHadTransaction;
        private final java.util.ArrayList<Boolean> persistTransactionStates = new java.util.ArrayList<>();

        private TestHandler(ObjectMapper objectMapper, InvestmentMarketSubscriptionRegistry subscriptionRegistry,
                            TransactionTemplate transactionTemplate, List<String> values) {
            super(objectMapper, subscriptionRegistry, transactionTemplate);
            this.values = values;
        }

        private TestHandler(ObjectMapper objectMapper, InvestmentMarketSubscriptionRegistry subscriptionRegistry,
                            TransactionTemplate transactionTemplate, MarketDataDimensionResolver dimensionResolver,
                            MarketDataQualityService qualityService, List<String> values) {
            super(objectMapper, subscriptionRegistry, transactionTemplate, dimensionResolver, qualityService);
            this.values = values;
        }

        @Override
        public InvestmentJobType jobType() {
            return InvestmentJobType.QUOTE_REFRESH;
        }

        @Override
        protected List<String> fetch(MarketDataJobInput input) {
            fetchHadTransaction = TransactionSynchronizationManager.isActualTransactionActive();
            return values;
        }

        @Override
        protected void validateSubscription(MarketDataJobInput input) {
            subscriptionRegistry().requireCapability(input.sourceCode(), input.productType(), input.symbol(),
                    input.capability());
        }

        @Override
        protected int persistPage(InvestmentJobClaim claim, MarketDataJobInput input, List<String> page,
                                  List<String> previousPage) {
            persistTransactionStates.add(TransactionSynchronizationManager.isActualTransactionActive());
            return page.size();
        }
    }

    private static class RecordingTransactionManager extends AbstractPlatformTransactionManager {

        private int commits;
        private int rollbacks;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }
    }

    private static class PagedTestHandler extends AbstractMarketDataJobHandler<String> {

        private final Function<MarketDataJobInput, List<String>> fetcher;
        private final java.util.ArrayList<Boolean> persistTransactionStates = new java.util.ArrayList<>();
        private boolean fetchHadTransaction;
        private String failPersistValue;

        private PagedTestHandler(ObjectMapper objectMapper,
                                 InvestmentMarketSubscriptionRegistry subscriptionRegistry,
                                 TransactionTemplate transactionTemplate,
                                 Function<MarketDataJobInput, List<String>> fetcher) {
            super(objectMapper, subscriptionRegistry, transactionTemplate);
            this.fetcher = fetcher;
        }

        @Override
        public InvestmentJobType jobType() {
            return InvestmentJobType.FUNDING_RATE_INCREMENTAL;
        }

        @Override
        protected List<String> fetch(MarketDataJobInput input) {
            fetchHadTransaction = TransactionSynchronizationManager.isActualTransactionActive();
            return fetcher.apply(input);
        }

        @Override
        protected void validateSubscription(MarketDataJobInput input) {
            subscriptionRegistry().requireCapability(input.sourceCode(), input.productType(), input.symbol(),
                    DataCapability.FUNDING_RATE);
        }

        @Override
        protected boolean providerPaged(MarketDataJobInput input) {
            return true;
        }

        @Override
        protected Instant nextPageStart(MarketDataJobInput input, List<String> page) {
            return input.startInclusive().plusSeconds(page.size());
        }

        @Override
        protected int persistPage(InvestmentJobClaim claim, MarketDataJobInput input, List<String> page,
                                  List<String> previousPage) {
            persistTransactionStates.add(TransactionSynchronizationManager.isActualTransactionActive());
            if (failPersistValue != null && page.contains(failPersistValue)) {
                throw new IllegalStateException("page rollback");
            }
            return page.size();
        }
    }

    private record ShapeCase(InvestmentJobType jobType, MarketDataJobInput input) {
    }

    private record TierFixture(PositionTierSyncJobHandler handler, PositionTierProvider provider,
                               InvestmentPositionTierRepository tierRepository,
                               MarketDataCursorService cursorService, NamedParameterJdbcTemplate jdbcTemplate,
                               MarketDataQualityService qualityService,
                               MarketDataAfterCommitPublisher afterCommitPublisher,
                               InvestmentIngestCursorPo cursor, Instant observedAt) {
    }
}
