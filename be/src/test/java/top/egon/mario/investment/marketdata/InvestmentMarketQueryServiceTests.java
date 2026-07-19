package top.egon.mario.investment.marketdata;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.common.model.ProductType;
import top.egon.mario.investment.marketdata.query.InvestmentMarketQueryService;
import top.egon.mario.investment.marketdata.repository.jdbc.ContractQuoteJdbcRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.FundingRateJdbcRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.MarketBarJdbcRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.model.ContractQuoteRow;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketBarIntradayRow;
import top.egon.mario.investment.marketdata.subscription.InvestmentMarketSubscriptionRegistry;
import top.egon.mario.investment.marketdata.subscription.MarketSubscription;
import top.egon.mario.investment.marketdata.subscription.RetentionPolicy;
import top.egon.mario.investment.marketdata.subscription.SubscriptionSchedule;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifies bounded read projections without any ingestion-side dependency.
 */
class InvestmentMarketQueryServiceTests {

    private static final Instant CUTOFF = Instant.parse("2030-01-02T00:00:00Z");

    private EmbeddedDatabase database;
    private JdbcTemplate jdbc;
    private InvestmentMarketSubscriptionRegistry subscriptionRegistry;
    private ContractQuoteJdbcRepository quoteRepository;
    private MarketBarJdbcRepository barRepository;
    private FundingRateJdbcRepository fundingRateRepository;
    private InvestmentMarketQueryService service;
    private MarketSubscription subscription;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2)
                .build();
        jdbc = new JdbcTemplate(database);
        createProjectionSchema();
        subscriptionRegistry = mock(InvestmentMarketSubscriptionRegistry.class);
        quoteRepository = mock(ContractQuoteJdbcRepository.class);
        barRepository = mock(MarketBarJdbcRepository.class);
        fundingRateRepository = mock(FundingRateJdbcRepository.class);
        subscription = subscription("BTCUSDT");
        when(subscriptionRegistry.subscriptions()).thenReturn(List.of(subscription));
        service = new InvestmentMarketQueryService(
                new NamedParameterJdbcTemplate(database), subscriptionRegistry, quoteRepository,
                barRepository, fundingRateRepository, Clock.fixed(CUTOFF, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @Test
    void listsSubscribedInstrumentsWithStablePagingLosslessDecimalsAndFreshness() {
        seedInstrument(20, "ETHUSDT", "ETHUSDT");
        seedInstrument(10, "BTCUSDT", "BTCUSDT");
        when(subscriptionRegistry.subscriptions()).thenReturn(List.of(subscription("ETHUSDT"), subscription));
        seedQuote(10, "123.450000000000000000", CUTOFF.minusSeconds(30));
        seedQuote(20, "99.000000000000000001", CUTOFF.minusSeconds(120));

        var firstPage = service.listInstruments(1, 1, "active", "SYMBOL_ASC");
        var secondPage = service.listInstruments(2, 1, "ACTIVE", "SYMBOL_ASC");

        assertThat(firstPage.total()).isEqualTo(2);
        assertThat(firstPage.records()).singleElement().satisfies(instrument -> {
            assertThat(instrument.instrumentId()).isEqualTo(10L);
            assertThat(instrument.lastPrice()).isEqualTo("123.450000000000000000");
            assertThat(instrument.freshness().status()).isEqualTo("FRESH");
            assertThat(instrument.availableCapabilities()).contains("LATEST_TICKER", "MARKET_CANDLE");
        });
        assertThat(secondPage.records()).singleElement().satisfies(instrument -> {
            assertThat(instrument.instrumentId()).isEqualTo(20L);
            assertThat(instrument.freshness().status()).isEqualTo("STALE");
        });
    }

    @Test
    void returnsCandlesAscendingAndEnforcesCapabilityAndPointLimits() {
        seedInstrument(10, "BTCUSDT", "BTCUSDT");
        Instant first = Instant.parse("2030-01-01T00:00:00Z");
        Instant second = first.plusSeconds(60);
        MarketBarIntradayRow later = bar(second, "2");
        MarketBarIntradayRow earlier = bar(first, "1");
        when(subscriptionRegistry.requireCandle("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                BarInterval.M1, PriceType.MARKET)).thenReturn(subscription);
        when(barRepository.findCurrentIntraday(1L, 10L, PriceType.MARKET, BarInterval.M1,
                first, second.plusSeconds(60), 0, 100)).thenReturn(List.of(later, earlier));

        var candles = service.candles(10, PriceType.MARKET, BarInterval.M1,
                first, second.plusSeconds(60), null, 100);

        assertThat(candles).extracting(candle -> candle.openTime()).containsExactly(first, second);
        assertThat(candles).extracting(candle -> candle.close()).containsExactly("1", "2");
        assertThatThrownBy(() -> service.candles(10, PriceType.MARKET, BarInterval.M1,
                first, second, null, InvestmentMarketQueryService.MAX_CANDLE_POINTS + 1))
                .isInstanceOf(InvestmentException.class)
                .satisfies(exception -> assertThat(((InvestmentException) exception).getErrorCode())
                        .isEqualTo(InvestmentErrorCode.INVALID_REQUEST));
        verify(barRepository).findCurrentIntraday(1L, 10L, PriceType.MARKET, BarInterval.M1,
                first, second.plusSeconds(60), 0, 100);
    }

    @Test
    void acceptsOneHundredTwentyDayH4StreamingWindowButKeepsFineIntervalsBounded() {
        seedInstrument(10, "BTCUSDT", "BTCUSDT");
        Instant from = CUTOFF.minus(Duration.ofDays(120));
        when(subscriptionRegistry.requireCandle("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                BarInterval.H4, PriceType.MARKET)).thenReturn(subscription);
        when(barRepository.findCurrentIntraday(1L, 10L, PriceType.MARKET, BarInterval.H4,
                from, CUTOFF, 0, 720)).thenReturn(List.of());

        assertThat(service.candles(10, PriceType.MARKET, BarInterval.H4,
                from, CUTOFF, null, 720)).isEmpty();
        assertThatThrownBy(() -> service.candles(10, PriceType.MARKET, BarInterval.M1,
                from, CUTOFF, null, 720))
                .isInstanceOf(InvestmentException.class)
                .satisfies(exception -> assertThat(((InvestmentException) exception).getErrorCode())
                        .isEqualTo(InvestmentErrorCode.INVALID_REQUEST));
        verify(barRepository).findCurrentIntraday(1L, 10L, PriceType.MARKET, BarInterval.H4,
                from, CUTOFF, 0, 720);
    }

    @Test
    void emptyProductionRegistryReturnsEmptyWithoutQueryingStorageOrStartingImport() {
        when(subscriptionRegistry.subscriptions()).thenReturn(List.of());
        NamedParameterJdbcTemplate untouchedJdbc = mock(NamedParameterJdbcTemplate.class);
        InvestmentMarketQueryService emptyService = new InvestmentMarketQueryService(
                untouchedJdbc, subscriptionRegistry, quoteRepository, barRepository, fundingRateRepository,
                Clock.fixed(CUTOFF, ZoneOffset.UTC));

        var result = emptyService.listInstruments(1, 20, null, "SYMBOL_ASC");

        assertThat(result.records()).isEmpty();
        assertThat(result.total()).isZero();
        verifyNoInteractions(untouchedJdbc, quoteRepository, barRepository, fundingRateRepository);
        assertThatThrownBy(() -> emptyService.listInstruments(1, 20, null, "source_time desc"))
                .isInstanceOf(InvestmentException.class);
    }

    @Test
    void currentQuoteCapturesResponseCutoffAfterReadingAndNeverReturnsANewerFact() {
        seedInstrument(10, "BTCUSDT", "BTCUSDT");
        Clock readClock = mock(Clock.class);
        Instant responseCutoff = CUTOFF.plusSeconds(5);
        ContractQuoteRow quote = quoteRow(responseCutoff.minusSeconds(2), responseCutoff.minusSeconds(1));
        when(quoteRepository.findLatest(1L, 10L)).thenReturn(java.util.Optional.of(quote));
        when(readClock.instant()).thenReturn(responseCutoff);
        InvestmentMarketQueryService timedService = new InvestmentMarketQueryService(
                new NamedParameterJdbcTemplate(database), subscriptionRegistry, quoteRepository,
                barRepository, fundingRateRepository, readClock);

        var response = timedService.quote(10L);

        assertThat(response.dataAsOf()).isEqualTo(responseCutoff);
        assertThat(response.sourceTime()).isBeforeOrEqualTo(response.dataAsOf());
        assertThat(response.receivedAt()).isBeforeOrEqualTo(response.dataAsOf());
        var order = inOrder(quoteRepository, readClock);
        order.verify(quoteRepository).findLatest(1L, 10L);
        order.verify(readClock).instant();
    }

    @Test
    void realRepositorySqlSelectsCurrentAndAsOfBarAndFundingRevisions() {
        seedInstrument(10, "BTCUSDT", "BTCUSDT");
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(database);
        MarketBarJdbcRepository actualBars = new MarketBarJdbcRepository(named, null);
        FundingRateJdbcRepository actualFunding = new FundingRateJdbcRepository(named, null);
        InvestmentMarketQueryService actualService = new InvestmentMarketQueryService(
                named, subscriptionRegistry, quoteRepository, actualBars, actualFunding,
                Clock.fixed(CUTOFF, ZoneOffset.UTC));
        Instant point = CUTOFF.minus(Duration.ofDays(10));
        Instant revisionBoundary = CUTOFF.minus(Duration.ofDays(2));
        seedIntradayRevision(point, 1, 1, "1", CUTOFF.minus(Duration.ofDays(20)), revisionBoundary);
        seedIntradayRevision(point, 2, 0, "2", revisionBoundary, null);
        seedFundingRevision(point, 1, 1, "0.001", CUTOFF.minus(Duration.ofDays(20)), revisionBoundary);
        seedFundingRevision(point, 2, 0, "0.002", revisionBoundary, null);
        seedFundingRevision(point.plusMillis(500), 1, 0, "0.999", CUTOFF.plusSeconds(1),
                CUTOFF.minus(Duration.ofDays(20)), null);
        when(subscriptionRegistry.requireCandle("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                BarInterval.M1, PriceType.MARKET)).thenReturn(subscription);

        assertThat(actualService.candles(10L, PriceType.MARKET, BarInterval.M1,
                point, point.plusSeconds(60), revisionBoundary.minusSeconds(1), 10))
                .singleElement().satisfies(candle -> {
                    assertThat(candle.close()).isEqualTo("1.000000000000000000");
                    assertThat(candle.revision()).isEqualTo(1L);
                });
        assertThat(actualService.candles(10L, PriceType.MARKET, BarInterval.M1,
                point, point.plusSeconds(60), null, 10))
                .singleElement().satisfies(candle -> {
                    assertThat(candle.close()).isEqualTo("2.000000000000000000");
                    assertThat(candle.revision()).isEqualTo(2L);
                });
        assertThat(actualService.fundingRates(10L, point, point.plusSeconds(1),
                revisionBoundary.minusSeconds(1), 1, 10).records())
                .singleElement().satisfies(rate -> {
                    assertThat(rate.fundingRate()).isEqualTo("0.001000000000");
                    assertThat(rate.revision()).isEqualTo(1L);
                });
        assertThat(actualService.fundingRates(10L, point, point.plusSeconds(1), null, 1, 10).records())
                .singleElement().satisfies(rate -> {
                    assertThat(rate.fundingRate()).isEqualTo("0.002000000000");
                    assertThat(rate.revision()).isEqualTo(2L);
                });
    }

    @Test
    void positionTierAsOfRequiresObservedAndIngestedCutoffInOuterAndInnerQueries() {
        seedInstrument(10, "BTCUSDT", "BTCUSDT");
        Instant visibleObserved = CUTOFF.minus(Duration.ofDays(2));
        Instant lateObserved = CUTOFF.minus(Duration.ofDays(1));
        seedTier(1, visibleObserved, CUTOFF.minus(Duration.ofDays(1)), 1);
        seedTier(2, visibleObserved, CUTOFF.plusSeconds(1), 2);
        seedTier(3, lateObserved, CUTOFF.plusSeconds(1), 1);

        var tiers = service.positionTiers(10L, CUTOFF);

        assertThat(tiers).singleElement().satisfies(tier -> {
            assertThat(tier.tierLevel()).isEqualTo(1);
            assertThat(tier.observedAt()).isEqualTo(visibleObserved);
            assertThat(tier.dataAsOf()).isEqualTo(CUTOFF);
        });
    }

    private void createProjectionSchema() {
        jdbc.execute("create table investment_venue (id bigint primary key, code varchar(64), deleted boolean)");
        jdbc.execute("""
                create table investment_instrument (
                    id bigint primary key, venue_id bigint, product_type varchar(32), contract_type varchar(32),
                    symbol varchar(128), base_asset varchar(32), quote_asset varchar(32),
                    settlement_asset varchar(32), margin_asset varchar(32), status varchar(32),
                    launch_time timestamp with time zone, deleted boolean)
                """);
        jdbc.execute("""
                create table investment_data_source (
                    id bigint primary key, code varchar(64), status varchar(32), deleted boolean)
                """);
        jdbc.execute("""
                create table investment_instrument_source (
                    id bigint primary key, instrument_id bigint, source_id bigint, external_symbol varchar(128),
                    source_status varchar(32), deleted boolean)
                """);
        jdbc.execute("""
                create table investment_contract_quote_latest (
                    source_id bigint, instrument_id bigint, last_price numeric(38,18), mark_price numeric(38,18),
                    index_price numeric(38,18), bid_price numeric(38,18), ask_price numeric(38,18),
                    bid_quantity numeric(38,18), ask_quantity numeric(38,18), open_24h numeric(38,18),
                    high_24h numeric(38,18), low_24h numeric(38,18), base_volume_24h numeric(38,18),
                    quote_volume_24h numeric(38,18), change_24h numeric(24,12), funding_rate numeric(24,12),
                    next_funding_time timestamp with time zone, open_interest numeric(38,18),
                    source_time timestamp with time zone, received_at timestamp with time zone, version bigint)
                """);
        jdbc.execute("create table investment_data_quality_issue (instrument_id bigint, deleted boolean, "
                + "resolution_status varchar(32), point_time timestamp with time zone)");
        jdbc.execute("""
                create table investment_market_bar_intraday (
                    source_id bigint, instrument_id bigint, price_type varchar(32), interval_code varchar(32),
                    open_time timestamp with time zone, close_time timestamp with time zone,
                    open_price numeric(38,18), high_price numeric(38,18), low_price numeric(38,18),
                    close_price numeric(38,18), base_volume numeric(38,18), quote_volume numeric(38,18),
                    is_closed boolean, source_updated_at timestamp with time zone,
                    ingested_at timestamp with time zone, revision bigint, revision_slot bigint,
                    valid_from timestamp with time zone, valid_to timestamp with time zone, checksum varchar(128))
                """);
        jdbc.execute("""
                create table investment_funding_rate (
                    source_id bigint, instrument_id bigint, funding_time timestamp with time zone,
                    funding_rate numeric(24,12), ingested_at timestamp with time zone,
                    revision bigint, revision_slot bigint, valid_from timestamp with time zone,
                    valid_to timestamp with time zone, checksum varchar(128))
                """);
        jdbc.execute("""
                create table investment_position_tier (
                    id bigint primary key, source_id bigint, instrument_id bigint,
                    observed_at timestamp with time zone, ingested_at timestamp with time zone,
                    tier_level integer, start_notional numeric(38,18), end_notional numeric(38,18),
                    max_leverage numeric(24,12), maintenance_margin_rate numeric(24,12))
                """);
        jdbc.update("insert into investment_venue (id, code, deleted) values (1, 'TEST', false)");
        jdbc.update("insert into investment_data_source (id, code, status, deleted) values (1, 'TEST', 'ACTIVE', false)");
    }

    private void seedInstrument(long id, String symbol, String externalSymbol) {
        jdbc.update("""
                insert into investment_instrument (
                    id, venue_id, product_type, contract_type, symbol, base_asset, quote_asset,
                    settlement_asset, margin_asset, status, launch_time, deleted)
                values (?, 1, 'USDT_FUTURES', 'PERPETUAL', ?, ?, 'USDT', 'USDT', 'USDT', 'ACTIVE', ?, false)
                """, id, symbol, symbol.substring(0, 3), CUTOFF.minus(Duration.ofDays(100)).atOffset(ZoneOffset.UTC));
        jdbc.update("""
                insert into investment_instrument_source (
                    id, instrument_id, source_id, external_symbol, source_status, deleted)
                values (?, ?, 1, ?, 'ACTIVE', false)
                """, id, id, externalSymbol);
    }

    private void seedQuote(long instrumentId, String price, Instant sourceTime) {
        jdbc.update("""
                insert into investment_contract_quote_latest (
                    source_id, instrument_id, last_price, mark_price, change_24h,
                    source_time, received_at, version)
                values (1, ?, ?, ?, 0.001000000000, ?, ?, 0)
                """, instrumentId, new BigDecimal(price), new BigDecimal(price),
                sourceTime.atOffset(ZoneOffset.UTC), sourceTime.plusSeconds(1).atOffset(ZoneOffset.UTC));
    }

    private MarketBarIntradayRow bar(Instant openTime, String close) {
        return new MarketBarIntradayRow(
                1, 10, PriceType.MARKET, BarInterval.M1, openTime, openTime.plusSeconds(60),
                new BigDecimal("1"), new BigDecimal("3"), new BigDecimal("1"), new BigDecimal(close),
                BigDecimal.ZERO, BigDecimal.ZERO, true, openTime, openTime, 1, openTime, null, "checksum");
    }

    private ContractQuoteRow quoteRow(Instant sourceTime, Instant receivedAt) {
        return new ContractQuoteRow(
                1L, 10L, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, BigDecimal.ZERO, sourceTime, receivedAt, 0L);
    }

    private void seedIntradayRevision(Instant openTime, long revision, long revisionSlot,
                                      String close, Instant validFrom, Instant validTo) {
        jdbc.update("""
                insert into investment_market_bar_intraday (
                    source_id, instrument_id, price_type, interval_code, open_time, close_time,
                    open_price, high_price, low_price, close_price, base_volume, quote_volume,
                    is_closed, source_updated_at, ingested_at, revision, revision_slot,
                    valid_from, valid_to, checksum)
                values (1, 10, 'MARKET', 'M1', ?, ?, 1, 3, 1, ?, 0, 0, true, ?, ?, ?, ?, ?, ?, ?)
                """, openTime.atOffset(ZoneOffset.UTC), openTime.plusSeconds(60).atOffset(ZoneOffset.UTC),
                new BigDecimal(close), openTime.atOffset(ZoneOffset.UTC), validFrom.atOffset(ZoneOffset.UTC),
                revision, revisionSlot, validFrom.atOffset(ZoneOffset.UTC),
                validTo == null ? null : validTo.atOffset(ZoneOffset.UTC), "bar-" + revision);
    }

    private void seedFundingRevision(Instant fundingTime, long revision, long revisionSlot,
                                     String rate, Instant validFrom, Instant validTo) {
        seedFundingRevision(fundingTime, revision, revisionSlot, rate, validFrom, validFrom, validTo);
    }

    private void seedFundingRevision(Instant fundingTime, long revision, long revisionSlot,
                                     String rate, Instant ingestedAt, Instant validFrom, Instant validTo) {
        jdbc.update("""
                insert into investment_funding_rate (
                    source_id, instrument_id, funding_time, funding_rate, ingested_at,
                    revision, revision_slot, valid_from, valid_to, checksum)
                values (1, 10, ?, ?, ?, ?, ?, ?, ?, ?)
                """, fundingTime.atOffset(ZoneOffset.UTC), new BigDecimal(rate), ingestedAt.atOffset(ZoneOffset.UTC),
                revision, revisionSlot, validFrom.atOffset(ZoneOffset.UTC),
                validTo == null ? null : validTo.atOffset(ZoneOffset.UTC), "funding-" + revision);
    }

    private void seedTier(long id, Instant observedAt, Instant ingestedAt, int tierLevel) {
        jdbc.update("""
                insert into investment_position_tier (
                    id, source_id, instrument_id, observed_at, ingested_at, tier_level,
                    start_notional, end_notional, max_leverage, maintenance_margin_rate)
                values (?, 1, 10, ?, ?, ?, 0, 100, 10, 0.01)
                """, id, observedAt.atOffset(ZoneOffset.UTC), ingestedAt.atOffset(ZoneOffset.UTC), tierLevel);
    }

    private MarketSubscription subscription(String symbol) {
        Set<DataCapability> capabilities = Set.of(
                DataCapability.MARKET_CANDLE, DataCapability.LATEST_TICKER,
                DataCapability.FUNDING_RATE, DataCapability.POSITION_TIER);
        return new MarketSubscription(
                "TEST", ProductType.USDT_FUTURES, symbol, Set.of(BarInterval.M1), Set.of(PriceType.MARKET),
                capabilities, new SubscriptionSchedule(Map.of(
                DataCapability.MARKET_CANDLE, Duration.ofMinutes(1),
                DataCapability.LATEST_TICKER, Duration.ofMinutes(1),
                DataCapability.FUNDING_RATE, Duration.ofHours(1),
                DataCapability.POSITION_TIER, Duration.ofHours(1)), Map.of()),
                new RetentionPolicy(Set.of(), Map.of(BarInterval.M1, Duration.ofDays(30))));
    }
}
