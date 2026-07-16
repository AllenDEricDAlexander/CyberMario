package top.egon.mario.investment.marketdata;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.marketdata.repository.jdbc.ContractQuoteJdbcRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.MarketBarJdbcRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.model.ContractQuoteWrite;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketBarDailyWrite;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketBarIntradayWrite;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketDataWriteContext;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(InvestmentMarketDataJdbcRepositoryTests.FixedClockConfiguration.class)
class InvestmentMarketDataJdbcRepositoryTests {

    private static final Instant T1 = Instant.parse("2030-01-01T00:00:00Z");
    private static final Instant LOCK_TIME = T1.minusSeconds(60);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MarketBarJdbcRepository barRepository;

    @Autowired
    private ContractQuoteJdbcRepository quoteRepository;

    private long sourceId;
    private long instrumentId;
    private long jobId;

    @BeforeEach
    void setUp() {
        clearMarketData();
        seedCatalogAndJob();
    }

    @Test
    void intradayBatchUsesJdbcOrderingAndSameChecksumDoesNotCreateARevision() {
        seedCursor("BAR_INTRADAY", "MARK", "H1");
        MarketBarIntradayWrite later = intraday(T1.plusSeconds(3600), "bar-2", "201");
        MarketBarIntradayWrite earlier = intraday(T1, "bar-1", "101");

        var first = barRepository.writeIntradayBatch(context(T1.plusSeconds(10)), List.of(later, earlier));
        var noOp = barRepository.writeIntradayRevision(context(T1.plusSeconds(20)), earlier);

        assertThat(first.inserted()).isEqualTo(2);
        assertThat(first.revised()).isZero();
        assertThat(noOp.unchanged()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from investment_market_bar_intraday", Integer.class)).isEqualTo(2);
        assertThat(barRepository.findCurrentIntraday(sourceId, instrumentId, PriceType.MARK, BarInterval.H1,
                T1.minusSeconds(1), T1.plusSeconds(7200), 0, 10))
                .extracting(row -> row.openTime())
                .containsExactly(T1, T1.plusSeconds(3600));
        assertThat(jdbcTemplate.queryForObject("""
                select last_checksum from investment_ingest_cursor
                where source_id = ? and instrument_id = ? and data_type = 'BAR_INTRADAY'
                """, String.class, sourceId, instrumentId)).isEqualTo("bar-1");
    }

    @Test
    void dailyBarsUseJdbcRevisionMappingRatherThanJpaEntities() {
        seedCursor("BAR_DAILY", "MARKET", "D1");
        MarketBarDailyWrite value = new MarketBarDailyWrite(sourceId, instrumentId, PriceType.MARKET,
                LocalDate.of(2030, 1, 1), decimal("100"), decimal("110"), decimal("90"), decimal("105"),
                decimal("12"), decimal("1200"), true, T1, T1.plusSeconds(1), "daily-1");

        var result = barRepository.writeDailyRevision(context(T1.plusSeconds(2)), value);

        assertThat(result.inserted()).isEqualTo(1);
        assertThat(barRepository.findCurrentDaily(sourceId, instrumentId, PriceType.MARKET,
                LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 2), 0, 10))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.revision()).isEqualTo(1);
                    assertThat(row.closePrice()).isEqualByComparingTo("105");
                });
    }

    @Test
    void staleEffectiveAtStillRevisesAndCursorProgressNeverMovesBackwardOrClears() {
        seedCursor("BAR_INTRADAY", "MARK", "H1");
        Instant openTime = T1;
        Instant furthestNextStart = T1.plusSeconds(300);
        Instant futureCursorTime = Instant.parse("2100-01-01T00:00:00Z");
        jdbcTemplate.update("""
                update investment_ingest_cursor
                set next_start_time = ?, last_success_time = ?, updated_at = ?
                where source_id = ? and instrument_id = ? and data_type = 'BAR_INTRADAY'
                """, furthestNextStart.atOffset(ZoneOffset.UTC), futureCursorTime.atOffset(ZoneOffset.UTC),
                futureCursorTime.atOffset(ZoneOffset.UTC), sourceId, instrumentId);
        MarketDataWriteContext firstContext = new MarketDataWriteContext(
                jobId, Instant.EPOCH, furthestNextStart);
        MarketDataWriteContext staleBackfillContext = new MarketDataWriteContext(
                jobId, Instant.EPOCH, T1.plusSeconds(100));
        MarketDataWriteContext nullCursorContext = new MarketDataWriteContext(
                jobId, Instant.EPOCH, null);

        barRepository.writeIntradayRevision(firstContext, intraday(openTime, "stale-v1", "101"));
        Instant futureValidFrom = Instant.parse("2099-01-01T00:00:00Z");
        jdbcTemplate.update("""
                update investment_market_bar_intraday set valid_from = ?
                where source_id = ? and instrument_id = ? and revision_slot = 0
                """, futureValidFrom.atOffset(ZoneOffset.UTC), sourceId, instrumentId);
        var revised = barRepository.writeIntradayRevision(
                staleBackfillContext, intraday(openTime, "stale-v2", "102"));
        var noOp = barRepository.writeIntradayRevision(
                nullCursorContext, intraday(openTime, "stale-v2", "102"));

        assertThat(revised.revised()).isEqualTo(1);
        assertThat(noOp.unchanged()).isEqualTo(1);
        assertThat(barRepository.findCurrentIntraday(sourceId, instrumentId, PriceType.MARK, BarInterval.H1,
                T1.minusSeconds(1), T1.plusSeconds(3600), 0, 10))
                .singleElement().satisfies(row -> {
                    assertThat(row.revision()).isEqualTo(2);
                    assertThat(row.validFrom()).isEqualTo(futureValidFrom.plus(1, ChronoUnit.MICROS));
                });
        assertThat(jdbcTemplate.queryForObject("""
                select next_start_time from investment_ingest_cursor
                where source_id = ? and instrument_id = ? and data_type = 'BAR_INTRADAY'
                """, OffsetDateTime.class, sourceId, instrumentId).toInstant())
                .isEqualTo(furthestNextStart);
        OffsetDateTime lastSuccessTime = jdbcTemplate.queryForObject("""
                select last_success_time from investment_ingest_cursor
                where source_id = ? and instrument_id = ? and data_type = 'BAR_INTRADAY'
                """, OffsetDateTime.class, sourceId, instrumentId);
        OffsetDateTime updatedAt = jdbcTemplate.queryForObject("""
                select updated_at from investment_ingest_cursor
                where source_id = ? and instrument_id = ? and data_type = 'BAR_INTRADAY'
                """, OffsetDateTime.class, sourceId, instrumentId);
        assertThat(lastSuccessTime.toInstant()).isEqualTo(futureCursorTime);
        assertThat(updatedAt.toInstant()).isEqualTo(futureCursorTime);
        assertThat(lastSuccessTime.toInstant()).isAfter(LOCK_TIME);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from investment_data_quality_issue where issue_code = 'UNEXPECTED_REVISION'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void latestQuoteBatchUpsertUsesStrictSourceAndReceiveTimeOrdering() {
        ContractQuoteWrite btc = quote(T1.plusSeconds(10), T1.plusSeconds(20), "100");
        ContractQuoteWrite eth = new ContractQuoteWrite(sourceId, instrumentId + 1,
                decimal("200"), decimal("200"), decimal("200"), decimal("199"), decimal("201"),
                decimal("1"), decimal("1"), null, null, null, null, null, null, null,
                null, decimal("10"), T1.plusSeconds(10), T1.plusSeconds(11));
        seedSecondInstrument();

        assertThat(quoteRepository.writeLatestBatch(List.of(btc, eth))).isEqualTo(2);
        assertThat(quoteRepository.writeLatest(
                quote(T1.plusSeconds(9), T1.plusSeconds(30), "90"))).isZero();
        assertThat(quoteRepository.writeLatest(
                quote(T1.plusSeconds(10), T1.plusSeconds(19), "91"))).isZero();
        assertThat(quoteRepository.writeLatest(btc)).isZero();

        assertThat(quoteRepository.findLatest(sourceId, instrumentId)).get().satisfies(row -> {
            assertThat(row.lastPrice()).isEqualByComparingTo("100");
            assertThat(row.sourceTime()).isEqualTo(T1.plusSeconds(10));
            assertThat(row.receivedAt()).isEqualTo(T1.plusSeconds(20));
            assertThat(row.version()).isZero();
        });

        assertThat(quoteRepository.writeLatest(
                quote(T1.plusSeconds(10), T1.plusSeconds(21), "101"))).isEqualTo(1);
        assertThat(quoteRepository.writeLatest(
                quote(T1.plusSeconds(10), T1.plusSeconds(21), "102"))).isZero();
        assertThat(quoteRepository.findLatest(sourceId, instrumentId)).get().satisfies(row -> {
            assertThat(row.lastPrice()).isEqualByComparingTo("101");
            assertThat(row.sourceTime()).isEqualTo(T1.plusSeconds(10));
            assertThat(row.receivedAt()).isEqualTo(T1.plusSeconds(21));
            assertThat(row.version()).isEqualTo(1);
        });

        assertThat(quoteRepository.writeLatest(
                quote(T1.plusSeconds(11), T1.plusSeconds(5), "103"))).isEqualTo(1);
        assertThat(quoteRepository.findLatest(sourceId, instrumentId)).get().satisfies(row -> {
            assertThat(row.lastPrice()).isEqualByComparingTo("103");
            assertThat(row.sourceTime()).isEqualTo(T1.plusSeconds(11));
            assertThat(row.receivedAt()).isEqualTo(T1.plusSeconds(5));
            assertThat(row.version()).isEqualTo(2);
        });
    }

    private MarketBarIntradayWrite intraday(Instant openTime, String checksum, String close) {
        return new MarketBarIntradayWrite(sourceId, instrumentId, PriceType.MARK, BarInterval.H1,
                openTime, openTime.plusSeconds(3600), decimal("100"), decimal("250"), decimal("90"),
                decimal(close), decimal("12"), decimal("1200"), true,
                openTime.plusSeconds(3600), openTime.plusSeconds(3601), checksum);
    }

    private ContractQuoteWrite quote(Instant sourceTime, Instant receivedAt, String lastPrice) {
        return new ContractQuoteWrite(sourceId, instrumentId,
                decimal(lastPrice), decimal(lastPrice), decimal(lastPrice), decimal("99"), decimal("101"),
                decimal("1"), decimal("1"), null, null, null, null, null, null, null,
                null, decimal("10"), sourceTime, receivedAt);
    }

    private MarketDataWriteContext context(Instant effectiveAt) {
        return new MarketDataWriteContext(jobId, effectiveAt, effectiveAt.plusSeconds(1));
    }

    private void seedCatalogAndJob() {
        jdbcTemplate.update("""
                insert into investment_venue(code, name, status) values ('BITGET', 'Bitget', 'ACTIVE')
                """);
        long venueId = jdbcTemplate.queryForObject(
                "select id from investment_venue where code = 'BITGET'", Long.class);
        jdbcTemplate.update("""
                insert into investment_data_source(
                    venue_id, code, provider_type, api_family, product_type, rate_limit_per_second, status
                ) values (?, 'BITGET_TEST', 'TEST', 'V2', 'USDT_FUTURES', 10, 'ACTIVE')
                """, venueId);
        sourceId = jdbcTemplate.queryForObject(
                "select id from investment_data_source where code = 'BITGET_TEST'", Long.class);
        instrumentId = insertInstrument(venueId, "BTCUSDT");
        jdbcTemplate.update("""
                insert into investment_job(job_type, idempotency_key)
                values ('CONTRACT_SYNC', 'market-jdbc-test')
                """);
        jobId = jdbcTemplate.queryForObject(
                "select id from investment_job where idempotency_key = 'market-jdbc-test'", Long.class);
    }

    private void seedSecondInstrument() {
        long venueId = jdbcTemplate.queryForObject("select id from investment_venue where code = 'BITGET'", Long.class);
        long second = insertInstrument(venueId, "ETHUSDT");
        assertThat(second).isEqualTo(instrumentId + 1);
    }

    private long insertInstrument(long venueId, String symbol) {
        jdbcTemplate.update("""
                insert into investment_instrument(
                    venue_id, market_type, product_type, contract_type, symbol,
                    base_asset, quote_asset, settlement_asset, margin_asset, status
                ) values (?, 'FUTURES', 'USDT_FUTURES', 'PERPETUAL', ?,
                    ?, 'USDT', 'USDT', 'USDT', 'ACTIVE')
                """, venueId, symbol, symbol.substring(0, 3));
        return jdbcTemplate.queryForObject(
                "select id from investment_instrument where symbol = ?", Long.class, symbol);
    }

    private void seedCursor(String dataType, String priceType, String interval) {
        jdbcTemplate.update("""
                insert into investment_ingest_cursor(
                    source_id, instrument_id, data_type, price_type, interval_code, updated_at
                ) values (?, ?, ?, ?, ?, ?)
                """, sourceId, instrumentId, dataType, priceType, interval, T1.minusSeconds(1));
    }

    private void clearMarketData() {
        for (String table : List.of("investment_data_quality_issue", "investment_market_bar_intraday",
                "investment_market_bar_daily", "investment_funding_rate", "investment_contract_quote_latest",
                "investment_ingest_cursor", "investment_job", "investment_instrument_source",
                "investment_contract_spec", "investment_position_tier", "investment_instrument",
                "investment_data_source", "investment_venue")) {
            jdbcTemplate.update("delete from " + table);
        }
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock marketRevisionTestClock() {
            return Clock.fixed(LOCK_TIME, ZoneOffset.UTC);
        }
    }
}
