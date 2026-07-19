package top.egon.mario.investment;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InvestmentBitgetCatalogMigrationTests {

    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateCatalog() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:investment_bitget_%s;MODE=PostgreSQL;"
                .formatted(UUID.randomUUID())
                + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("50"))
                .load();
        flyway.migrate();
        flyway.validate();
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void seedsOnlyTheBitgetBtcAndSolPublicMarketCatalog() {
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from investment_venue where code = 'BITGET'", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from investment_data_source where code = 'BITGET'", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select cast(capabilities_json as varchar)
                from investment_data_source
                where code = 'BITGET'
                """, String.class)).contains("MARKET_CANDLE", "FUNDING_RATE", "CURRENT_FUNDING_RATE")
                .doesNotContain("LATEST_TICKER");
        assertThat(jdbcTemplate.queryForList("""
                select symbol from investment_instrument
                where product_type = 'USDT_FUTURES'
                order by symbol
                """, String.class)).containsExactly("BTCUSDT", "SOLUSDT");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from investment_instrument_source", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from investment_contract_spec", Integer.class)).isZero();
    }

    @Test
    void seedsExactlyTheCursorDimensionsNeededByCodeSubscriptions() {
        assertThat(jdbcTemplate.queryForList("""
                select distinct data_type || ':' || price_type || ':' || interval_code
                from investment_ingest_cursor
                order by 1
                """, String.class)).containsExactlyElementsOf(List.of(
                "BAR_DAILY:MARKET:D1",
                "BAR_INTRADAY:MARKET:M1",
                "FUNDING_RATE:NONE:NONE",
                "QUOTE:NONE:NONE"
        ));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from investment_ingest_cursor", Integer.class)).isEqualTo(8);
    }
}
