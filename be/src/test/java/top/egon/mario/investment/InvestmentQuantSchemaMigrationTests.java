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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InvestmentQuantSchemaMigrationTests {

    private static final List<String> QUANT_TABLES = List.of(
            "investment_strategy_release",
            "investment_dataset_snapshot",
            "investment_dataset_snapshot_item",
            "investment_backtest_run",
            "investment_backtest_trade",
            "investment_backtest_event",
            "investment_backtest_equity_point"
    );

    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateSchema() {
        dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:investment_quant_%s;MODE=PostgreSQL;"
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
    void flywayCreatesAllQuantTables() {
        List<String> tables = jdbcTemplate.queryForList("""
                select table_name
                from information_schema.tables
                where table_schema = 'public'
                  and table_name in (
                    'investment_strategy_release',
                    'investment_dataset_snapshot',
                    'investment_dataset_snapshot_item',
                    'investment_backtest_run',
                    'investment_backtest_trade',
                    'investment_backtest_event',
                    'investment_backtest_equity_point'
                  )
                order by table_name
                """, String.class);

        assertThat(tables).containsExactlyInAnyOrderElementsOf(QUANT_TABLES);
    }

    @Test
    void schemaPreservesJsonNumericAndHashContracts() throws SQLException {
        for (String column : List.of(
                "investment_strategy_release.required_capabilities_json",
                "investment_strategy_release.descriptor_snapshot_json",
                "investment_dataset_snapshot.intervals_json",
                "investment_dataset_snapshot.price_types_json",
                "investment_dataset_snapshot.contract_spec_snapshot_json",
                "investment_dataset_snapshot.position_tier_snapshot_json",
                "investment_dataset_snapshot.fee_model_snapshot_json",
                "investment_dataset_snapshot.slippage_model_snapshot_json",
                "investment_backtest_run.extra_metrics_json",
                "investment_backtest_event.details_json")) {
            assertThat(columnMetadata(column).typeName()).as(column + " JSON type").isIn("JSON", "JSONB");
        }

        for (String column : List.of(
                "investment_strategy_release.source_hash",
                "investment_dataset_snapshot.contract_spec_hash",
                "investment_dataset_snapshot.position_tier_hash",
                "investment_dataset_snapshot.funding_data_hash",
                "investment_dataset_snapshot.dataset_hash",
                "investment_dataset_snapshot_item.data_hash")) {
            ColumnMetadata metadata = columnMetadata(column);
            assertThat(metadata.precision()).as(column + " length").isEqualTo(64);
            assertThat(metadata.nullable()).as(column + " nullable").isFalse();
        }

        for (String column : List.of(
                "investment_backtest_run.initial_equity",
                "investment_backtest_trade.entry_price",
                "investment_backtest_event.amount",
                "investment_backtest_equity_point.equity")) {
            ColumnMetadata metadata = columnMetadata(column);
            assertThat(metadata.jdbcType()).as(column + " JDBC type").isEqualTo(Types.NUMERIC);
            assertThat(metadata.precision()).as(column + " precision").isEqualTo(38);
            assertThat(metadata.scale()).as(column + " scale").isEqualTo(18);
        }
    }

    @Test
    void schemaLocksOwnershipAndParentRelationships() throws SQLException {
        assertForeignKey("investment_dataset_snapshot", "workspace_id", "investment_workspace");
        assertForeignKey("investment_dataset_snapshot", "source_id", "investment_data_source");
        assertForeignKey("investment_dataset_snapshot_item", "snapshot_id", "investment_dataset_snapshot");
        assertForeignKey("investment_dataset_snapshot_item", "instrument_id", "investment_instrument");
        assertForeignKey("investment_backtest_run", "workspace_id", "investment_workspace");
        assertForeignKey("investment_backtest_run", "job_id", "investment_job");
        assertForeignKey("investment_backtest_run", "strategy_release_id", "investment_strategy_release");
        assertForeignKey("investment_backtest_run", "dataset_snapshot_id", "investment_dataset_snapshot");
        assertForeignKey("investment_backtest_trade", "run_id", "investment_backtest_run");
        assertForeignKey("investment_backtest_event", "run_id", "investment_backtest_run");
        assertForeignKey("investment_backtest_equity_point", "run_id", "investment_backtest_run");
    }

    @Test
    void schemaEnforcesNaturalKeysAndQueryIndexes() throws SQLException {
        assertConstraintColumns("uk_investment_strategy_release_version", "strategy_code", "strategy_version");
        assertConstraintColumns("uk_investment_dataset_snapshot_hash", "workspace_id", "dataset_hash");
        assertConstraintColumns("uk_investment_dataset_snapshot_item_dimension",
                "snapshot_id", "instrument_id", "data_type", "price_type", "interval_code");
        assertConstraintColumns("uk_investment_backtest_run_job", "job_id");
        assertConstraintColumns("uk_investment_backtest_event_sequence", "run_id", "sequence_no");
        assertPrimaryKey("investment_backtest_equity_point", "run_id", "point_time");

        assertIndexColumns("idx_investment_dataset_snapshot_workspace_created",
                "workspace_id:ASC", "created_at:DESC");
        assertIndexColumns("idx_investment_backtest_run_workspace_created",
                "workspace_id:ASC", "created_at:DESC");
        assertIndexColumns("idx_investment_backtest_trade_run_entry", "run_id:ASC", "entry_time:ASC");
    }

    @Test
    void schemaRequiresSentinelsAndKeepsFactRowsFreeOfSoftDelete() throws SQLException {
        assertThat(columnMetadata("investment_dataset_snapshot_item.price_type").nullable()).isFalse();
        assertThat(columnMetadata("investment_dataset_snapshot_item.interval_code").nullable()).isFalse();
        assertCheckContains("chk_investment_dataset_snapshot_item_dimension",
                "data_type", "price_type", "interval_code", "none");

        for (String table : List.of(
                "investment_strategy_release",
                "investment_dataset_snapshot",
                "investment_dataset_snapshot_item",
                "investment_backtest_trade",
                "investment_backtest_event",
                "investment_backtest_equity_point")) {
            assertColumnAbsent(table, "deleted");
            assertColumnAbsent(table, "version");
        }
    }

    @Test
    void schemaConstrainsHashesAndBacktestState() {
        assertCheckContains("chk_investment_strategy_release_hash", "source_hash", "64");
        assertCheckContains("chk_investment_dataset_snapshot_hashes",
                "contract_spec_hash", "position_tier_hash", "funding_data_hash", "dataset_hash", "64");
        assertCheckContains("chk_investment_dataset_snapshot_item_hash", "data_hash", "64");
        assertCheckContains("chk_investment_dataset_snapshot_time", "start_time", "end_time", "data_as_of");
        assertCheckContains("chk_investment_backtest_run_state",
                "status", "initial_equity", "isolated", "one_way");
        assertCheckContains("chk_investment_backtest_trade_values", "quantity", "leverage", "entry_price");
        assertCheckContains("chk_investment_backtest_equity_values",
                "used_margin", "drawdown", "gross_exposure");
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
                    assertThat(keys.getShort("DELETE_RULE"))
                            .as(table + "." + column + " delete rule")
                            .isIn((short) DatabaseMetaData.importedKeyNoAction,
                                    (short) DatabaseMetaData.importedKeyRestrict);
                }
            }
        }
        assertThat(references).as(table + "." + column + " foreign key").containsExactly(referencedTable);
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

    private record ColumnMetadata(int jdbcType, String typeName, int precision, int scale, boolean nullable) {
    }
}
