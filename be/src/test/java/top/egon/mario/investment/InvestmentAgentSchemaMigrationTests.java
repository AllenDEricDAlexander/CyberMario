package top.egon.mario.investment;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InvestmentAgentSchemaMigrationTests {

    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateSchema() {
        dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:investment_agent_%s;MODE=PostgreSQL;"
                .formatted(UUID.randomUUID())
                + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
        flyway.validate();
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void flywayCreatesAgentRunAndDecisionTables() {
        List<String> tables = jdbcTemplate.queryForList("""
                select table_name
                from information_schema.tables
                where table_schema = 'public'
                  and table_name in ('investment_agent_run', 'investment_agent_decision')
                order by table_name
                """, String.class);

        assertThat(tables).containsExactly("investment_agent_decision", "investment_agent_run");
    }

    @Test
    void runOwnsWorkspaceBoundAccountAndReportAndGenericAudit() throws SQLException {
        assertCompositeForeignKey("investment_agent_run", "fk_investment_agent_run_account_workspace",
                List.of("account_id", "workspace_id"), "investment_paper_account",
                List.of("id", "workspace_id"));
        assertCompositeForeignKey("investment_agent_run", "fk_investment_agent_run_report_workspace",
                List.of("report_id", "workspace_id"), "investment_research_report",
                List.of("id", "workspace_id"));
        assertForeignKey("investment_agent_run", "workspace_id", "investment_workspace");
        assertForeignKey("investment_agent_run", "generic_agent_run_audit_id", "agent_run_audit");
        assertThat(columnMetadata("investment_agent_run.account_id").nullable()).isTrue();
        assertThat(columnMetadata("investment_agent_run.report_id").nullable()).isTrue();
    }

    @Test
    void decisionReferencesItsRunInstrumentAndOneIntent() throws SQLException {
        assertForeignKey("investment_agent_decision", "run_id", "investment_agent_run");
        assertForeignKey("investment_agent_decision", "instrument_id", "investment_instrument");
        assertForeignKey("investment_agent_decision", "intent_id", "investment_trade_intent");
        assertThat(columnMetadata("investment_agent_decision.instrument_id").nullable()).isTrue();
        assertThat(columnMetadata("investment_agent_decision.intent_id").nullable()).isTrue();
    }

    @Test
    void idempotencyAndIntentLinksAreUniquelyFenced() {
        assertConstraintColumns("uk_investment_agent_run_idempotency", "idempotency_key");
        assertConstraintColumns("uk_investment_agent_decision_intent", "intent_id");
        assertConstraintColumns("uk_investment_agent_decision_execution", "execution_idempotency_key");
    }

    @Test
    void runAndDecisionStateChecksFailClosed() {
        assertCheckContains("chk_investment_agent_run_state",
                "market_analysis", "instrument_analysis", "strategy_review", "portfolio_review",
                "auto_trade", "pending", "running", "succeeded", "failed",
                "account_id", "finished_at");
        assertCheckContains("chk_investment_agent_decision_values",
                "hold", "open_long", "open_short", "close", "reduce",
                "confidence", "requested_quantity", "requested_notional", "requested_leverage",
                "market", "limit", "limit_price", "validated");
        assertCheckContains("chk_investment_agent_decision_execution",
                "not_applicable", "pending", "submitted", "failed",
                "execution_idempotency_key", "intent_id", "hold");
    }

    @Test
    void analysisRecommendationCanRemainNonExecutableWhileHoldCanNeverExecute() {
        String insertRun = """
                insert into investment_agent_run (
                    workspace_id, agent_preset_code, generic_agent_run_audit_id, run_type, status,
                    data_as_of, started_at, finished_at, idempotency_key, created_at, updated_at,
                    version, deleted)
                values (?, 'INVESTMENT_ANALYST_V1', ?, 'INSTRUMENT_ANALYSIS', 'SUCCEEDED',
                    current_timestamp, current_timestamp, current_timestamp, ?, current_timestamp,
                    current_timestamp, 0, false)
                """;
        long workspaceId = insertWorkspace();
        long auditId = insertGenericAudit();
        long instrumentId = insertInstrument();
        jdbcTemplate.update(insertRun, workspaceId, auditId, "analysis-only-" + UUID.randomUUID());
        Long runId = jdbcTemplate.queryForObject(
                "select max(id) from investment_agent_run", Long.class);

        jdbcTemplate.update("""
                insert into investment_agent_decision (
                    run_id, instrument_id, action, confidence, horizon, thesis, risks_json, invalidation_json,
                    requested_quantity, requested_notional, requested_leverage, order_type,
                    execution_status, data_as_of, status, created_at)
                values (?, ?, 'OPEN_LONG', 0.75, 'INTRADAY', 'analysis only', '[]', '[]',
                    1, 100, 2, 'MARKET', 'NOT_APPLICABLE', current_timestamp, 'VALIDATED',
                    current_timestamp)
                """, runId, instrumentId);

        assertThat(jdbcTemplate.queryForObject("""
                select execution_status from investment_agent_decision where run_id = ?
                """, String.class, runId)).isEqualTo("NOT_APPLICABLE");
    }

    @Test
    void plannerIndexesMatchOwnerTimelineAndRecoveryQueries() {
        assertIndexColumns("idx_investment_agent_run_workspace_created",
                "workspace_id:ASC", "created_at:DESC");
        assertIndexColumns("idx_investment_agent_run_account_created",
                "account_id:ASC", "created_at:DESC");
        assertIndexColumns("idx_investment_agent_decision_run",
                "run_id:ASC", "id:ASC");
        assertIndexColumns("idx_investment_agent_decision_instrument_as_of",
                "instrument_id:ASC", "data_as_of:DESC");
        assertIndexColumns("idx_investment_agent_decision_execution_recovery",
                "execution_status:ASC", "intent_id:ASC", "id:ASC");
    }

    @Test
    void structuredDecisionKeepsExactDecimalsAndJsonEvidence() throws SQLException {
        for (String column : List.of(
                "investment_agent_decision.requested_quantity",
                "investment_agent_decision.requested_notional")) {
            assertNumeric(column, 38, 18);
        }
        for (String column : List.of(
                "investment_agent_decision.confidence",
                "investment_agent_decision.requested_leverage")) {
            assertNumeric(column, 24, 12);
        }
        for (String column : List.of(
                "investment_agent_run.input_snapshot_json",
                "investment_agent_decision.risks_json",
                "investment_agent_decision.invalidation_json")) {
            assertThat(columnMetadata(column).typeName()).as(column + " JSON type").isIn("JSON", "JSONB");
        }
        assertThat(columnMetadata("investment_agent_run.idempotency_key").nullable()).isFalse();
        assertThat(columnMetadata("investment_agent_decision.execution_idempotency_key").nullable()).isTrue();
    }

    @Test
    void decisionIsNotSoftDeletedOrVersioned() throws SQLException {
        assertColumnAbsent("investment_agent_decision", "deleted");
        assertColumnAbsent("investment_agent_decision", "updated_at");
        assertColumnAbsent("investment_agent_decision", "version");
    }

    private static ColumnMetadata columnMetadata(String qualifiedColumn) throws SQLException {
        String[] parts = qualifiedColumn.split("\\.", 2);
        try (var connection = dataSource.getConnection();
             ResultSet columns = connection.getMetaData().getColumns(null, "public", parts[0], parts[1])) {
            assertThat(columns.next()).as(qualifiedColumn + " exists").isTrue();
            return new ColumnMetadata(
                    columns.getInt("DATA_TYPE"),
                    columns.getString("TYPE_NAME").toUpperCase(Locale.ROOT),
                    columns.getInt("COLUMN_SIZE"),
                    columns.getInt("DECIMAL_DIGITS"),
                    columns.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
        }
    }

    private static void assertNumeric(String qualifiedColumn, int precision, int scale) throws SQLException {
        ColumnMetadata metadata = columnMetadata(qualifiedColumn);
        assertThat(metadata.jdbcType()).as(qualifiedColumn + " JDBC type").isEqualTo(Types.NUMERIC);
        assertThat(metadata.precision()).as(qualifiedColumn + " precision").isEqualTo(precision);
        assertThat(metadata.scale()).as(qualifiedColumn + " scale").isEqualTo(scale);
    }

    private static void assertColumnAbsent(String table, String column) throws SQLException {
        try (var connection = dataSource.getConnection();
             ResultSet columns = connection.getMetaData().getColumns(null, "public", table, column)) {
            assertThat(columns.next()).as(table + "." + column + " absent").isFalse();
        }
    }

    private static void assertForeignKey(String table, String column, String referencedTable) throws SQLException {
        List<String> references = new ArrayList<>();
        try (var connection = dataSource.getConnection();
             ResultSet keys = connection.getMetaData().getImportedKeys(null, "public", table)) {
            while (keys.next()) {
                if (column.equals(keys.getString("FKCOLUMN_NAME"))) {
                    references.add(keys.getString("PKTABLE_NAME"));
                    assertRestrictDelete(table + "." + column, keys.getShort("DELETE_RULE"));
                }
            }
        }
        assertThat(references).as(table + "." + column + " foreign key").contains(referencedTable);
    }

    private static void assertCompositeForeignKey(String table, String foreignKeyName,
                                                  List<String> foreignColumns, String referencedTable,
                                                  List<String> referencedColumns) throws SQLException {
        List<ForeignKeyColumn> columns = new ArrayList<>();
        try (var connection = dataSource.getConnection();
             ResultSet keys = connection.getMetaData().getImportedKeys(null, "public", table)) {
            while (keys.next()) {
                if (foreignKeyName.equals(keys.getString("FK_NAME"))) {
                    columns.add(new ForeignKeyColumn(keys.getShort("KEY_SEQ"),
                            keys.getString("FKCOLUMN_NAME"), keys.getString("PKTABLE_NAME"),
                            keys.getString("PKCOLUMN_NAME")));
                    assertRestrictDelete(foreignKeyName, keys.getShort("DELETE_RULE"));
                }
            }
        }
        columns.sort(Comparator.comparingInt(ForeignKeyColumn::sequence));
        assertThat(columns).hasSize(foreignColumns.size());
        assertThat(columns).extracting(ForeignKeyColumn::foreignColumn).containsExactlyElementsOf(foreignColumns);
        assertThat(columns).extracting(ForeignKeyColumn::referencedTable).containsOnly(referencedTable);
        assertThat(columns).extracting(ForeignKeyColumn::referencedColumn)
                .containsExactlyElementsOf(referencedColumns);
    }

    private static void assertRestrictDelete(String description, short deleteRule) {
        assertThat(deleteRule).as(description + " delete rule")
                .isIn((short) DatabaseMetaData.importedKeyNoAction,
                        (short) DatabaseMetaData.importedKeyRestrict);
    }

    private static void assertConstraintColumns(String constraintName, String... expectedColumns) {
        List<String> columns = jdbcTemplate.queryForList("""
                select column_name
                from information_schema.key_column_usage
                where constraint_schema = 'public'
                  and constraint_name = ?
                order by ordinal_position
                """, String.class, constraintName);
        assertThat(columns).as(constraintName + " columns").containsExactly(expectedColumns);
    }

    private static void assertIndexColumns(String indexName, String... expectedColumns) {
        List<String> columns = jdbcTemplate.query("""
                select column_name, ordering_specification
                from information_schema.index_columns
                where index_schema = 'public'
                  and index_name = ?
                order by ordinal_position
                """, (resultSet, rowNum) -> resultSet.getString("column_name") + ":"
                + resultSet.getString("ordering_specification"), indexName);
        assertThat(columns).as(indexName + " columns").containsExactly(expectedColumns);
    }

    private static void assertCheckContains(String constraintName, String... fragments) {
        List<String> clauses = jdbcTemplate.queryForList("""
                select check_clause
                from information_schema.check_constraints
                where constraint_schema = 'public'
                  and constraint_name = ?
                """, String.class, constraintName);
        assertThat(clauses).as(constraintName + " exists").hasSize(1);
        assertThat(clauses.getFirst().toLowerCase(Locale.ROOT)).contains(fragments);
    }

    private static long insertWorkspace() {
        jdbcTemplate.update("""
                insert into investment_workspace (
                    owner_user_id, name, base_currency, timezone, status, settings_json,
                    created_at, updated_at, version, deleted)
                values (1, ?, 'USDT', 'UTC', 'ACTIVE', '{}', current_timestamp,
                    current_timestamp, 0, false)
                """, "agent-schema-" + UUID.randomUUID());
        return jdbcTemplate.queryForObject("select max(id) from investment_workspace", Long.class);
    }

    private static long insertGenericAudit() {
        jdbcTemplate.update("""
                insert into agent_run_audit (
                    thread_id, status, model_call_count, tool_call_count, mcp_tool_call_count,
                    started_at, created_at)
                values (?, 'SUCCESS', 0, 0, 0, current_timestamp, current_timestamp)
                """, "investment-schema-" + UUID.randomUUID());
        return jdbcTemplate.queryForObject("select max(id) from agent_run_audit", Long.class);
    }

    private static long insertInstrument() {
        jdbcTemplate.update("""
                insert into investment_venue (
                    code, name, status, created_at, updated_at, version, deleted)
                values (?, 'Schema venue', 'ACTIVE', current_timestamp,
                    current_timestamp, 0, false)
                """, "SCHEMA_" + UUID.randomUUID().toString().replace("-", ""));
        Long venueId = jdbcTemplate.queryForObject("select max(id) from investment_venue", Long.class);
        jdbcTemplate.update("""
                insert into investment_instrument (
                    venue_id, market_type, product_type, contract_type, symbol, base_asset,
                    quote_asset, settlement_asset, margin_asset, status, created_at, updated_at,
                    version, deleted)
                values (?, 'FUTURES', 'USDT_FUTURES', 'PERPETUAL', ?, 'BTC', 'USDT',
                    'USDT', 'USDT', 'ACTIVE', current_timestamp, current_timestamp, 0, false)
                """, venueId, "BTCUSDT_" + UUID.randomUUID().toString().replace("-", ""));
        return jdbcTemplate.queryForObject("select max(id) from investment_instrument", Long.class);
    }

    private record ColumnMetadata(int jdbcType, String typeName, int precision, int scale, boolean nullable) {
    }

    private record ForeignKeyColumn(int sequence, String foreignColumn, String referencedTable,
                                    String referencedColumn) {
    }
}
