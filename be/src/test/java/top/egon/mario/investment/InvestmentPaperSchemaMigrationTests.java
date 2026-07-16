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

class InvestmentPaperSchemaMigrationTests {

    private static final List<String> PAPER_TABLES = List.of(
            "investment_paper_account",
            "investment_risk_profile",
            "investment_trade_intent",
            "investment_risk_check",
            "investment_paper_order",
            "investment_paper_fill",
            "investment_margin_ledger",
            "investment_position",
            "investment_account_snapshot"
    );

    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateSchema() {
        dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:investment_paper_%s;MODE=PostgreSQL;"
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
    void flywayCreatesAllPaperTradingTables() {
        List<String> tables = jdbcTemplate.queryForList("""
                select table_name
                from information_schema.tables
                where table_schema = 'public'
                  and table_name like 'investment_%'
                """, String.class);

        assertThat(tables).containsAll(PAPER_TABLES);
    }

    @Test
    void accountAndRiskProfileUseExplicitSafeLimits() throws SQLException {
        ColumnMetadata ledgerSequence = columnMetadata("investment_paper_account.ledger_sequence");
        assertThat(ledgerSequence.nullable()).isFalse();
        assertThat(ledgerSequence.defaultValue()).contains("0");
        assertConstraintColumns("uk_investment_paper_account_workspace_name", "workspace_id", "name");
        assertConstraintColumns("uk_investment_risk_profile_account", "account_id");

        for (String column : List.of(
                "max_order_notional",
                "max_position_notional",
                "max_gross_exposure_notional",
                "max_daily_loss_amount")) {
            assertNumeric("investment_risk_profile." + column, 38, 18);
        }
        assertNumeric("investment_risk_profile.max_drawdown_ratio", 24, 12);
        assertNumeric("investment_risk_profile.max_slippage_bps", 24, 12);
        assertCheckContains("chk_investment_risk_profile_limits",
                "max_leverage", "max_order_notional", "max_position_notional",
                "max_gross_exposure_notional", "max_daily_loss_amount",
                "max_drawdown_ratio", "max_orders_per_hour", "cooldown_seconds",
                "max_market_data_age_seconds", "max_slippage_bps");
    }

    @Test
    void naturalKeysAndPlannerIndexesMatchWriteAndQueryPaths() throws SQLException {
        assertConstraintColumns("uk_investment_trade_intent_idempotency", "idempotency_key");
        assertConstraintColumns("uk_investment_risk_check_rule", "intent_id", "rule_code");
        assertConstraintColumns("uk_investment_paper_order_client", "client_order_id");
        assertConstraintColumns("uk_investment_paper_order_intent", "intent_id");
        assertConstraintColumns("uk_investment_paper_fill_number", "order_id", "fill_no");
        assertConstraintColumns("uk_investment_margin_ledger_sequence", "account_id", "sequence_no");
        assertConstraintColumns("uk_investment_margin_ledger_idempotency", "idempotency_key");
        assertConstraintColumns("uk_investment_position_instrument", "account_id", "instrument_id");
        assertPrimaryKey("investment_account_snapshot", "account_id", "snapshot_time");

        assertIndexColumns("idx_investment_trade_intent_account_created",
                "account_id:ASC", "created_at:DESC");
        assertIndexColumns("idx_investment_paper_order_account_status_submitted",
                "account_id:ASC", "status:ASC", "submitted_at:ASC");
        assertIndexColumns("idx_investment_paper_order_instrument_status_submitted",
                "instrument_id:ASC", "status:ASC", "submitted_at:ASC");
        assertIndexColumns("idx_investment_margin_ledger_account_occurred",
                "account_id:ASC", "occurred_at:DESC");
    }

    @Test
    void redundantOwnershipAndInstrumentColumnsAreCompositeForeignKeyFenced() throws SQLException {
        assertForeignKey("investment_paper_account", "workspace_id", "investment_workspace");
        assertCompositeForeignKey("investment_trade_intent", "fk_investment_trade_intent_account_workspace",
                List.of("account_id", "workspace_id"), "investment_paper_account",
                List.of("id", "workspace_id"));
        assertForeignKey("investment_trade_intent", "instrument_id", "investment_instrument");
        assertCompositeForeignKey("investment_paper_order", "fk_investment_paper_order_intent_scope",
                List.of("intent_id", "workspace_id", "account_id", "instrument_id"),
                "investment_trade_intent", List.of("id", "workspace_id", "account_id", "instrument_id"));
        assertCompositeForeignKey("investment_paper_fill", "fk_investment_paper_fill_order_instrument",
                List.of("order_id", "instrument_id"), "investment_paper_order",
                List.of("id", "instrument_id"));
        assertForeignKey("investment_position", "account_id", "investment_paper_account");
        assertForeignKey("investment_position", "instrument_id", "investment_instrument");
        assertForeignKey("investment_account_snapshot", "account_id", "investment_paper_account");
    }

    @Test
    void stateAndMonetaryChecksFailClosed() {
        assertCheckContains("chk_investment_paper_account_state",
                "initial_equity", "ledger_sequence", "usdt", "isolated", "one_way", "status");
        assertCheckContains("chk_investment_trade_intent_values",
                "order_type", "quantity", "requested_notional", "leverage", "limit_price");
        assertCheckContains("chk_investment_paper_order_values",
                "quantity", "remaining_quantity", "leverage", "order_type", "limit_price");
        assertCheckContains("chk_investment_paper_fill_values",
                "fill_no", "fill_price", "quantity", "notional", "fee_rate", "fee_amount");
        assertCheckContains("chk_investment_position_values",
                "quantity", "entry_price", "leverage", "isolated_margin",
                "maintenance_margin_rate", "maintenance_margin", "liquidation_price");
        assertCheckContains("chk_investment_account_snapshot_values",
                "used_margin", "maintenance_margin", "gross_exposure", "drawdown", "position_count");
    }

    @Test
    void appendOnlyFactsHaveNoSoftDeleteOrUpdateVersion() throws SQLException {
        for (String table : List.of(
                "investment_risk_check",
                "investment_paper_fill",
                "investment_margin_ledger",
                "investment_account_snapshot")) {
            assertColumnAbsent(table, "deleted");
            assertColumnAbsent(table, "updated_at");
            assertColumnAbsent(table, "version");
        }
        for (String column : List.of(
                "investment_risk_profile.settings_json",
                "investment_risk_check.details_json",
                "investment_margin_ledger.details_json")) {
            assertThat(columnMetadata(column).typeName()).isIn("JSON", "JSONB");
        }
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
                    columns.getInt("NULLABLE") == DatabaseMetaData.columnNullable,
                    columns.getString("COLUMN_DEF"));
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

    private static void assertPrimaryKey(String table, String... expectedColumns) throws SQLException {
        List<Map.Entry<Integer, String>> columns = new ArrayList<>();
        try (var connection = dataSource.getConnection();
             ResultSet keys = connection.getMetaData().getPrimaryKeys(null, "public", table)) {
            while (keys.next()) {
                columns.add(Map.entry(keys.getInt("KEY_SEQ"), keys.getString("COLUMN_NAME")));
            }
        }
        columns.sort(Map.Entry.comparingByKey());
        assertThat(columns.stream().map(Map.Entry::getValue).toList())
                .as(table + " primary key").containsExactly(expectedColumns);
    }

    private static void assertForeignKey(String table, String column, String referencedTable) throws SQLException {
        List<String> references = new ArrayList<>();
        try (var connection = dataSource.getConnection();
             ResultSet keys = connection.getMetaData().getImportedKeys(null, "public", table)) {
            while (keys.next()) {
                if (column.equals(keys.getString("FKCOLUMN_NAME"))) {
                    references.add(keys.getString("PKTABLE_NAME"));
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

    private record ColumnMetadata(int jdbcType, String typeName, int precision, int scale,
                                  boolean nullable, String defaultValue) {
    }

    private record ForeignKeyColumn(int sequence, String foreignColumn,
                                    String referencedTable, String referencedColumn) {
    }
}
