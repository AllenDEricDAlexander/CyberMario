package top.egon.mario.investment.marketdata;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.marketdata.repository.jdbc.FundingRateJdbcRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.MarketBarJdbcRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.model.FundingRateWrite;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketBarIntradayRow;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketBarIntradayWrite;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketDataWriteContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class InvestmentAsOfMarketDataRepositoryTests {

    private static final Instant BASE = Instant.parse("2031-02-03T00:00:00Z");
    private static final Instant FIRST_VALID_FROM = BASE.plusSeconds(10);
    private static final Instant SECOND_VALID_FROM = BASE.plusSeconds(20);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MarketBarJdbcRepository barRepository;

    @Autowired
    private FundingRateJdbcRepository fundingRepository;

    private long sourceId;
    private long instrumentId;
    private long jobId;

    @BeforeEach
    void setUp() {
        clearMarketData();
        seedCatalogJobAndCursors();
    }

    @Test
    void correctedBarRetainsHistoryAndResolvesCurrentOrHistoricalDataAsOf() {
        Instant openTime = BASE.plusSeconds(3600);
        MarketBarIntradayWrite original = bar(openTime, "bar-v1", "105");
        MarketBarIntradayWrite corrected = bar(openTime, "bar-v2", "108");

        var inserted = barRepository.writeIntradayRevision(context(FIRST_VALID_FROM), original);
        var revised = barRepository.writeIntradayRevision(context(SECOND_VALID_FROM), corrected);
        MarketBarIntradayRow current = barRepository.findCurrentIntraday(
                sourceId, instrumentId, PriceType.MARK, BarInterval.H1,
                BASE, BASE.plusSeconds(7200), 0, 10).getFirst();
        Instant revisionBoundary = current.validFrom();

        assertThat(inserted.inserted()).isEqualTo(1);
        assertThat(revised.revised()).isEqualTo(1);
        assertThat(barRepository.findIntradayAsOf(sourceId, instrumentId, PriceType.MARK, BarInterval.H1,
                BASE, BASE.plusSeconds(7200), revisionBoundary.minus(1, ChronoUnit.MICROS), 0, 10))
                .singleElement().satisfies(row -> {
                    assertThat(row.revision()).isEqualTo(1);
                    assertThat(row.closePrice()).isEqualByComparingTo("105");
                    assertThat(row.validTo()).isEqualTo(revisionBoundary);
                    assertThat(row.validFrom()).isBefore(revisionBoundary);
                });
        assertThat(barRepository.findIntradayAsOf(sourceId, instrumentId, PriceType.MARK, BarInterval.H1,
                BASE, BASE.plusSeconds(7200), revisionBoundary, 0, 10))
                .singleElement().satisfies(row -> {
                    assertThat(row.revision()).isEqualTo(2);
                    assertThat(row.closePrice()).isEqualByComparingTo("108");
                    assertThat(row.validTo()).isNull();
                });
        assertThat(barRepository.findCurrentIntraday(sourceId, instrumentId, PriceType.MARK, BarInterval.H1,
                BASE, BASE.plusSeconds(7200), 0, 10))
                .extracting(MarketBarIntradayRow::revision)
                .containsExactly(2L);

        assertThat(jdbcTemplate.queryForList("""
                select revision, revision_slot from investment_market_bar_intraday
                where source_id = ? and instrument_id = ? order by revision
                """, sourceId, instrumentId))
                .extracting(row -> ((Number) row.get("revision")).longValue(),
                        row -> ((Number) row.get("revision_slot")).longValue())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1L, 1L),
                        org.assertj.core.groups.Tuple.tuple(2L, 0L));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from investment_data_quality_issue where issue_code = 'UNEXPECTED_REVISION'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void revisingAnUnclosedBarRetainsHistoryWithoutReportingAnUnexpectedRevision() {
        Instant openTime = BASE.plusSeconds(3600);
        barRepository.writeIntradayRevision(context(FIRST_VALID_FROM),
                bar(openTime, "live-v1", "105", false));

        var revised = barRepository.writeIntradayRevision(context(SECOND_VALID_FROM),
                bar(openTime, "live-v2", "108", false));

        assertThat(revised.revised()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList("""
                select revision, revision_slot from investment_market_bar_intraday
                where source_id = ? and instrument_id = ? order by revision
                """, sourceId, instrumentId))
                .extracting(row -> ((Number) row.get("revision")).longValue(),
                        row -> ((Number) row.get("revision_slot")).longValue())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1L, 1L),
                        org.assertj.core.groups.Tuple.tuple(2L, 0L));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from investment_data_quality_issue", Integer.class)).isZero();
    }

    @Test
    void asOfOrderingAndOffsetPagingAreDeterministicAfterOnePointIsRevised() {
        List<MarketBarIntradayWrite> initial = List.of(
                bar(BASE.plusSeconds(7200), "bar-c", "103"),
                bar(BASE, "bar-a", "101"),
                bar(BASE.plusSeconds(3600), "bar-b", "102")
        );
        barRepository.writeIntradayBatch(context(FIRST_VALID_FROM), initial);
        barRepository.writeIntradayRevision(context(SECOND_VALID_FROM),
                bar(BASE.plusSeconds(3600), "bar-b2", "202"));

        List<MarketBarIntradayRow> full = barRepository.findIntradayAsOf(
                sourceId, instrumentId, PriceType.MARK, BarInterval.H1,
                BASE, BASE.plusSeconds(10800), SECOND_VALID_FROM.plusSeconds(1), 0, 10);
        List<MarketBarIntradayRow> middlePage = barRepository.findIntradayAsOf(
                sourceId, instrumentId, PriceType.MARK, BarInterval.H1,
                BASE, BASE.plusSeconds(10800), SECOND_VALID_FROM.plusSeconds(1), 1, 1);

        assertThat(full).extracting(MarketBarIntradayRow::openTime)
                .containsExactly(BASE, BASE.plusSeconds(3600), BASE.plusSeconds(7200));
        assertThat(full).extracting(MarketBarIntradayRow::revision)
                .containsExactly(1L, 2L, 1L);
        assertThat(middlePage).singleElement().satisfies(row -> {
            assertThat(row.openTime()).isEqualTo(BASE.plusSeconds(3600));
            assertThat(row.closePrice()).isEqualByComparingTo("202");
        });
    }

    @Test
    void fundingRevisionUsesTheSameHalfOpenAsOfValidityRule() {
        Instant fundingTime = BASE.plusSeconds(28800);
        FundingRateWrite original = new FundingRateWrite(sourceId, instrumentId, fundingTime,
                decimal("0.0001"), FIRST_VALID_FROM, "funding-v1");
        FundingRateWrite corrected = new FundingRateWrite(sourceId, instrumentId, fundingTime,
                decimal("0.0002"), SECOND_VALID_FROM, "funding-v2");

        fundingRepository.writeRevision(context(FIRST_VALID_FROM), original);
        Instant futureValidFrom = Instant.parse("2099-02-03T00:00:00Z");
        jdbcTemplate.update("""
                update investment_funding_rate set valid_from = ?
                where source_id = ? and instrument_id = ? and revision_slot = 0
                """, futureValidFrom.atOffset(ZoneOffset.UTC), sourceId, instrumentId);
        fundingRepository.writeRevision(context(SECOND_VALID_FROM), corrected);
        Instant revisionBoundary = fundingRepository.findCurrent(
                sourceId, instrumentId, BASE, BASE.plusSeconds(30000), 0, 10)
                .getFirst().validFrom();
        assertThat(revisionBoundary).isEqualTo(futureValidFrom.plus(1, ChronoUnit.MICROS));

        assertThat(fundingRepository.findAsOf(sourceId, instrumentId, BASE, BASE.plusSeconds(30000),
                revisionBoundary.minus(1, ChronoUnit.MICROS), 0, 10))
                .singleElement().satisfies(row -> {
                    assertThat(row.revision()).isEqualTo(1);
                    assertThat(row.fundingRate()).isEqualByComparingTo("0.0001");
                    assertThat(row.validTo()).isEqualTo(revisionBoundary);
                    assertThat(row.validFrom()).isBefore(revisionBoundary);
                });
        assertThat(fundingRepository.findAsOf(sourceId, instrumentId, BASE, BASE.plusSeconds(30000),
                revisionBoundary, 0, 10))
                .singleElement().satisfies(row -> {
                    assertThat(row.revision()).isEqualTo(2);
                    assertThat(row.fundingRate()).isEqualByComparingTo("0.0002");
                });
        assertThat(fundingRepository.findCurrent(sourceId, instrumentId,
                BASE, BASE.plusSeconds(30000), 0, 10))
                .singleElement().extracting(row -> row.revision()).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "select issue_code from investment_data_quality_issue", String.class))
                .isEqualTo("UNEXPECTED_REVISION");
    }

    @Test
    void revisionCursorAndQualityChangesRollbackTogetherWhenTheJobFenceIsInvalid() {
        Instant openTime = BASE.plusSeconds(3600);
        barRepository.writeIntradayRevision(context(FIRST_VALID_FROM), bar(openTime, "atomic-v1", "105"));

        MarketDataWriteContext missingJob = new MarketDataWriteContext(
                Long.MAX_VALUE, SECOND_VALID_FROM, SECOND_VALID_FROM.plusSeconds(1));
        assertThatThrownBy(() -> barRepository.writeIntradayRevision(
                missingJob, bar(openTime, "atomic-v2", "108")))
                .isInstanceOf(RuntimeException.class);

        assertThat(jdbcTemplate.queryForList("""
                select revision, revision_slot, checksum from investment_market_bar_intraday
                where source_id = ? and instrument_id = ?
                """, sourceId, instrumentId))
                .singleElement().satisfies(row -> {
                    assertThat(((Number) row.get("revision")).longValue()).isEqualTo(1L);
                    assertThat(((Number) row.get("revision_slot")).longValue()).isZero();
                    assertThat(row.get("checksum")).isEqualTo("atomic-v1");
                });
        assertThat(jdbcTemplate.queryForObject("""
                select last_checksum from investment_ingest_cursor
                where source_id = ? and instrument_id = ? and data_type = 'BAR_INTRADAY'
                """, String.class, sourceId, instrumentId)).isEqualTo("atomic-v1");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from investment_data_quality_issue", Integer.class)).isZero();
    }

    private MarketBarIntradayWrite bar(Instant openTime, String checksum, String closePrice) {
        return bar(openTime, checksum, closePrice, true);
    }

    private MarketBarIntradayWrite bar(Instant openTime, String checksum, String closePrice, boolean closed) {
        return new MarketBarIntradayWrite(sourceId, instrumentId, PriceType.MARK, BarInterval.H1,
                openTime, openTime.plusSeconds(3600), decimal("100"), decimal("250"), decimal("90"),
                decimal(closePrice), decimal("12"), decimal("1200"), closed,
                openTime.plusSeconds(3600), openTime.plusSeconds(3601), checksum);
    }

    private MarketDataWriteContext context(Instant effectiveAt) {
        return new MarketDataWriteContext(jobId, effectiveAt, effectiveAt.plusSeconds(1));
    }

    private void seedCatalogJobAndCursors() {
        jdbcTemplate.update("insert into investment_venue(code, name, status) values ('ASOF', 'AsOf', 'ACTIVE')");
        long venueId = jdbcTemplate.queryForObject(
                "select id from investment_venue where code = 'ASOF'", Long.class);
        jdbcTemplate.update("""
                insert into investment_data_source(
                    venue_id, code, provider_type, api_family, product_type, rate_limit_per_second, status
                ) values (?, 'ASOF_SOURCE', 'TEST', 'V2', 'USDT_FUTURES', 10, 'ACTIVE')
                """, venueId);
        sourceId = jdbcTemplate.queryForObject(
                "select id from investment_data_source where code = 'ASOF_SOURCE'", Long.class);
        jdbcTemplate.update("""
                insert into investment_instrument(
                    venue_id, market_type, product_type, contract_type, symbol,
                    base_asset, quote_asset, settlement_asset, margin_asset, status
                ) values (?, 'FUTURES', 'USDT_FUTURES', 'PERPETUAL', 'BTCUSDT',
                    'BTC', 'USDT', 'USDT', 'USDT', 'ACTIVE')
                """, venueId);
        instrumentId = jdbcTemplate.queryForObject(
                "select id from investment_instrument where symbol = 'BTCUSDT'", Long.class);
        jdbcTemplate.update("""
                insert into investment_job(job_type, idempotency_key)
                values ('BAR_BACKFILL', 'market-asof-test')
                """);
        jobId = jdbcTemplate.queryForObject(
                "select id from investment_job where idempotency_key = 'market-asof-test'", Long.class);
        seedCursor("BAR_INTRADAY", "MARK", "H1");
        seedCursor("FUNDING_RATE", "NONE", "NONE");
    }

    private void seedCursor(String dataType, String priceType, String interval) {
        jdbcTemplate.update("""
                insert into investment_ingest_cursor(
                    source_id, instrument_id, data_type, price_type, interval_code, updated_at
                ) values (?, ?, ?, ?, ?, ?)
                """, sourceId, instrumentId, dataType, priceType, interval, BASE.minusSeconds(1));
    }

    private void clearMarketData() {
        for (String table : List.of("investment_backtest_trade", "investment_backtest_run",
                "investment_dataset_snapshot_item", "investment_dataset_snapshot",
                "investment_data_quality_issue", "investment_market_bar_intraday",
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
}
