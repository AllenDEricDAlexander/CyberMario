package top.egon.mario.investment;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InvestmentExpandedCandleCursorMigrationTests {

    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateExpandedCandleCursors() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:investment_expanded_cursors_%s;MODE=PostgreSQL;"
                .formatted(UUID.randomUUID())
                + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        migrateTo(dataSource, "50");
        jdbcTemplate = new JdbcTemplate(dataSource);
        seedPreUpgradeState();
        migrateTo(dataSource, "51");
    }

    @Test
    void seedsEveryCodeDeclaredBitgetCandleCursorWithoutReplacingExistingState() {
        assertThat(jdbcTemplate.queryForList("""
                select distinct data_type || ':' || price_type || ':' || interval_code
                from investment_ingest_cursor
                order by 1
                """, String.class)).containsExactlyInAnyOrder(
                "BAR_DAILY:MARKET:D1",
                "BAR_INTRADAY:MARKET:M1",
                "BAR_INTRADAY:MARKET:M5",
                "BAR_INTRADAY:MARKET:M15",
                "BAR_INTRADAY:MARKET:M30",
                "BAR_INTRADAY:MARKET:H1",
                "BAR_INTRADAY:MARKET:H4",
                "FUNDING_RATE:NONE:NONE",
                "QUOTE:NONE:NONE"
        );
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from investment_ingest_cursor", Integer.class)).isEqualTo(18);
        assertThat(jdbcTemplate.queryForList("""
                select interval_code, count(*) as cursor_count
                from investment_ingest_cursor
                where data_type = 'BAR_INTRADAY'
                  and price_type = 'MARKET'
                  and interval_code in ('M5', 'M15', 'M30', 'H1', 'H4')
                group by interval_code
                """)).allSatisfy(row -> assertThat(row.get("cursor_count")).isEqualTo(2L));
        assertThat(jdbcTemplate.queryForObject("""
                select cursor.status
                from investment_ingest_cursor cursor
                join investment_instrument instrument on instrument.id = cursor.instrument_id
                where instrument.symbol = 'BTCUSDT'
                  and cursor.data_type = 'BAR_INTRADAY'
                  and cursor.price_type = 'MARKET'
                  and cursor.interval_code = 'M1'
                """, String.class)).isEqualTo("SUCCEEDED");
    }

    @Test
    void requeuesOnlyFailedCandleJobsBlockedByMissingExpandedCursors() {
        List<Map<String, Object>> repairedJobs = jdbcTemplate.queryForList("""
                select status, attempts, last_error_code, last_error_message, started_at, finished_at
                from investment_job
                where idempotency_key like 'migration-v51-missing-%'
                """);
        assertThat(repairedJobs).hasSize(5).allSatisfy(repairedJob -> {
            assertThat(repairedJob.get("status")).isEqualTo("PENDING");
            assertThat(repairedJob.get("attempts")).isEqualTo(0);
            assertThat(repairedJob.get("last_error_code")).isNull();
            assertThat(repairedJob.get("last_error_message")).isNull();
            assertThat(repairedJob.get("started_at")).isNull();
            assertThat(repairedJob.get("finished_at")).isNull();
        });

        Map<String, Object> unrelatedJob = jdbcTemplate.queryForMap("""
                select status, attempts, last_error_code, last_error_message
                from investment_job
                where idempotency_key = 'migration-v51-unrelated-failure'
                """);
        assertThat(unrelatedJob.get("status")).isEqualTo("FAILED");
        assertThat(unrelatedJob.get("attempts")).isEqualTo(5);
        assertThat(unrelatedJob.get("last_error_code")).isEqualTo("UNEXPECTED_ERROR");
        assertThat(unrelatedJob.get("last_error_message")).isEqualTo("Market data provider request failed");
    }

    private static void migrateTo(DriverManagerDataSource dataSource, String version) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(version))
                .load();
        flyway.migrate();
        flyway.validate();
    }

    private static void seedPreUpgradeState() {
        jdbcTemplate.update("""
                update investment_ingest_cursor
                set status = 'SUCCEEDED'
                where instrument_id = (
                    select instrument.id
                    from investment_instrument instrument
                    where instrument.symbol = 'BTCUSDT'
                )
                  and data_type = 'BAR_INTRADAY'
                  and price_type = 'MARKET'
                  and interval_code = 'M1'
                """);
        for (String interval : List.of("M5", "M15", "M30", "H1", "H4")) {
            insertFailedJob(
                    "migration-v51-missing-" + interval.toLowerCase(),
                    interval,
                    "Ingestion cursor does not exist for BAR_INTRADAY:1:2:MARKET:" + interval);
        }
        insertFailedJob(
                "migration-v51-unrelated-failure",
                "H1",
                "Market data provider request failed");
    }

    private static void insertFailedJob(String idempotencyKey, String interval, String errorMessage) {
        jdbcTemplate.update("""
                insert into investment_job (
                    job_type, status, available_at, attempts, max_attempts,
                    idempotency_key, input_json, result_json,
                    last_error_code, last_error_message, started_at, finished_at,
                    created_at, updated_at
                ) values (
                    'BAR_BACKFILL', 'FAILED', CURRENT_TIMESTAMP, 5, 5,
                    ?, cast(? as jsonb), cast('{}' as jsonb),
                    'UNEXPECTED_ERROR', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, idempotencyKey, "{\"interval\":\"" + interval + "\"}", errorMessage);
    }
}
