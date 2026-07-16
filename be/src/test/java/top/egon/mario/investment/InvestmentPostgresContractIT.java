package top.egon.mario.investment;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvestmentPostgresContractIT {

    private InvestmentPostgresTestSupport database;

    @BeforeAll
    void migrateDisposablePostgres() {
        database = InvestmentPostgresTestSupport.create("investment_contract");
    }

    @AfterAll
    void dropDisposableSchema() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void fullFlywayChainAppliesInvestmentMigrationsAndValidatesRepeatedly() {
        JdbcTemplate jdbc = database.jdbc();

        for (int version = 39; version <= 44; version++) {
            Integer count = jdbc.queryForObject("""
                    select count(*)
                    from flyway_schema_history
                    where success = true and version = ?
                    """, Integer.class, String.valueOf(version));
            assertThat(count).as("successful Flyway migration V" + version).isEqualTo(1);
        }

        database.flyway().validate();
        MigrateResult repeated = database.flyway().migrate();
        assertThat(repeated.migrationsExecuted).as("repeat migrate must be a no-op").isZero();
        database.flyway().validate();
    }

    @Test
    void investmentColumnsUsePostgresJsonbTimestamptzAndNumericContracts() {
        assertColumn("investment_job", "input_json", "jsonb", null, null);
        assertColumn("investment_agent_run", "input_snapshot_json", "jsonb", null, null);
        assertColumn("investment_risk_check", "details_json", "jsonb", null, null);
        assertColumn("investment_market_bar_intraday", "open_time", "timestamptz", null, null);
        assertColumn("investment_agent_decision", "data_as_of", "timestamptz", null, null);
        assertColumn("investment_market_bar_intraday", "close_price", "numeric", 38, 18);
        assertColumn("investment_contract_spec", "max_leverage", "numeric", 24, 12);
        assertColumn("investment_agent_decision", "confidence", "numeric", 24, 12);
    }

    @Test
    void namedConstraintsAndOrderedIndexesMatchPersistenceContract() {
        List<String> constraints = List.of(
                "uk_investment_market_bar_intraday_slot",
                "uk_investment_funding_rate_slot",
                "uk_investment_job_idempotency",
                "uk_investment_trade_intent_scope",
                "fk_investment_paper_order_intent_scope",
                "uk_investment_margin_ledger_idempotency",
                "fk_investment_agent_run_account_workspace",
                "uk_investment_agent_decision_execution",
                "chk_investment_agent_decision_execution"
        );
        constraints.forEach(this::assertConstraintExists);

        assertIndexDefinition("idx_investment_job_dispatch",
                "(status, available_at, priority, id)");
        assertIndexDefinition("idx_investment_market_bar_intraday_lookup",
                "(instrument_id, price_type, interval_code, revision_slot, open_time desc)");
        assertIndexDefinition("idx_investment_paper_order_account_status_submitted",
                "(account_id, status, submitted_at)");
        assertIndexDefinition("idx_investment_agent_decision_execution_recovery",
                "(execution_status, intent_id, id)");
    }

    private void assertColumn(String table, String column, String udtName,
                              Integer precision, Integer scale) {
        var row = database.jdbc().queryForMap("""
                select udt_name, numeric_precision, numeric_scale
                from information_schema.columns
                where table_schema = current_schema()
                  and table_name = ?
                  and column_name = ?
                """, table, column);
        assertThat(row.get("udt_name")).as(table + "." + column + " type").isEqualTo(udtName);
        if (precision != null) {
            assertThat(((Number) row.get("numeric_precision")).intValue())
                    .as(table + "." + column + " precision").isEqualTo(precision);
            assertThat(((Number) row.get("numeric_scale")).intValue())
                    .as(table + "." + column + " scale").isEqualTo(scale);
        }
    }

    private void assertConstraintExists(String constraintName) {
        Integer count = database.jdbc().queryForObject("""
                select count(*)
                from pg_constraint c
                join pg_namespace n on n.oid = c.connamespace
                where n.nspname = current_schema() and c.conname = ?
                """, Integer.class, constraintName);
        assertThat(count).as("named constraint " + constraintName).isEqualTo(1);
    }

    private void assertIndexDefinition(String indexName, String orderedColumns) {
        String definition = database.jdbc().queryForObject("""
                select pg_get_indexdef(i.indexrelid)
                from pg_index i
                join pg_class idx on idx.oid = i.indexrelid
                join pg_class tbl on tbl.oid = i.indrelid
                join pg_namespace n on n.oid = tbl.relnamespace
                where n.nspname = current_schema() and idx.relname = ?
                """, String.class, indexName);
        assertThat(definition).as("index " + indexName + " ordered columns")
                .isNotNull()
                .satisfies(value -> assertThat(normalize(value)).contains(normalize(orderedColumns)));
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replace("\"", "").replaceAll("\\s+", " ");
    }
}

/**
 * Creates a unique schema in an explicitly configured disposable PostgreSQL database.
 */
final class InvestmentPostgresTestSupport implements AutoCloseable {

    private static final String REQUIRED_ENV_MESSAGE = """
            Investment PostgreSQL integration tests require INVESTMENT_POSTGRES_TEST_URL, \
            INVESTMENT_POSTGRES_TEST_USERNAME, and INVESTMENT_POSTGRES_TEST_PASSWORD; \
            the target database must be disposable.""";

    private final DriverManagerDataSource adminDataSource;
    private final DriverManagerDataSource schemaDataSource;
    private final JdbcTemplate jdbc;
    private final Flyway flyway;
    private final String schema;

    private InvestmentPostgresTestSupport(DriverManagerDataSource adminDataSource,
                                          DriverManagerDataSource schemaDataSource,
                                          Flyway flyway,
                                          String schema) {
        this.adminDataSource = adminDataSource;
        this.schemaDataSource = schemaDataSource;
        this.jdbc = new JdbcTemplate(schemaDataSource);
        this.flyway = flyway;
        this.schema = schema;
    }

    static InvestmentPostgresTestSupport create(String prefix) {
        String url = System.getenv("INVESTMENT_POSTGRES_TEST_URL");
        String username = System.getenv("INVESTMENT_POSTGRES_TEST_USERNAME");
        String password = System.getenv("INVESTMENT_POSTGRES_TEST_PASSWORD");
        if (isBlank(url) || isBlank(username) || isBlank(password)) {
            fail(REQUIRED_ENV_MESSAGE);
        }

        DriverManagerDataSource admin = dataSource(url, username, password);
        try (Connection ignored = admin.getConnection()) {
            // The preflight keeps missing credentials and a non-disposable target explicit.
        } catch (SQLException ex) {
            fail("Unable to connect to INVESTMENT_POSTGRES_TEST_URL: " + rootMessage(ex), ex);
        }

        String schema = prefix + "_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(admin).execute("create schema " + schema);
        DriverManagerDataSource scoped = dataSource(withCurrentSchema(url, schema), username, password);
        Flyway flyway = Flyway.configure()
                .dataSource(scoped)
                .locations("classpath:db/migration", "classpath:db/postgresql")
                .schemas(schema)
                .defaultSchema(schema)
                .cleanDisabled(true)
                .validateOnMigrate(true)
                .load();
        try {
            flyway.migrate();
            return new InvestmentPostgresTestSupport(admin, scoped, flyway, schema);
        } catch (RuntimeException ex) {
            new JdbcTemplate(admin).execute("drop schema if exists " + schema + " cascade");
            throw ex;
        }
    }

    JdbcTemplate jdbc() {
        return jdbc;
    }

    Flyway flyway() {
        return flyway;
    }

    DriverManagerDataSource dataSource() {
        return schemaDataSource;
    }

    void resetInvestmentData() {
        List<String> tables = jdbc.queryForList("""
                select table_name
                from information_schema.tables
                where table_schema = current_schema()
                  and table_type = 'BASE TABLE'
                  and table_name like 'investment\\_%' escape '\\'
                order by table_name
                """, String.class);
        if (!tables.isEmpty()) {
            jdbc.execute("truncate table " + String.join(", ", tables) + " restart identity cascade");
        }
        jdbc.execute("truncate table agent_run_event_audit, agent_run_audit restart identity cascade");
    }

    @Override
    public void close() {
        new JdbcTemplate(adminDataSource).execute("drop schema if exists " + schema + " cascade");
    }

    private static DriverManagerDataSource dataSource(String url, String username, String password) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    private static String withCurrentSchema(String url, String schema) {
        return url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
