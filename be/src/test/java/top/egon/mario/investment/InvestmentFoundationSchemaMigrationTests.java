package top.egon.mario.investment;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
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

class InvestmentFoundationSchemaMigrationTests {

    private static final List<String> FOUNDATION_TABLES = List.of(
            "investment_venue",
            "investment_data_source",
            "investment_instrument",
            "investment_instrument_source",
            "investment_contract_spec",
            "investment_position_tier",
            "investment_market_bar_daily",
            "investment_market_bar_intraday",
            "investment_contract_quote_latest",
            "investment_funding_rate",
            "investment_workspace",
            "investment_ingest_cursor",
            "investment_job",
            "investment_data_quality_issue",
            "investment_watchlist",
            "investment_watchlist_item",
            "investment_research_report",
            "investment_report_evidence"
    );

    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateSchema() {
        dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:investment_foundation_%s;MODE=PostgreSQL;"
                .formatted(UUID.randomUUID())
                + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("40"))
                .load();
        flyway.migrate();
        flyway.validate();
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void flywayCreatesAllFoundationTables() {
        List<String> tables = jdbcTemplate.queryForList("""
                select table_name
                from information_schema.tables
                where table_schema = 'public'
                  and table_name like 'investment_%'
                order by table_name
                """, String.class);

        assertThat(tables).containsExactlyInAnyOrderElementsOf(FOUNDATION_TABLES);
    }

    @Test
    void flywayPreservesJsonTimestampAndNumericMappings() throws SQLException {
        for (String column : List.of(
                "investment_venue.metadata_json",
                "investment_data_source.capabilities_json",
                "investment_data_source.settings_json",
                "investment_instrument_source.raw_metadata_json",
                "investment_contract_spec.raw_metadata_json",
                "investment_workspace.settings_json",
                "investment_job.input_json",
                "investment_job.result_json",
                "investment_data_quality_issue.details_json",
                "investment_research_report.metrics_json",
                "investment_report_evidence.metadata_json")) {
            ColumnMetadata metadata = columnMetadata(column);
            assertThat(metadata.typeName()).as(column + " JSON type")
                    .isIn("JSON", "JSONB");
        }

        assertThat(columnMetadata("investment_market_bar_intraday.open_time").jdbcType())
                .isEqualTo(Types.TIMESTAMP_WITH_TIMEZONE);
        assertThat(columnMetadata("investment_job.lease_expires_at").jdbcType())
                .isEqualTo(Types.TIMESTAMP_WITH_TIMEZONE);
        assertThat(columnMetadata("investment_research_report.data_as_of").jdbcType())
                .isEqualTo(Types.TIMESTAMP_WITH_TIMEZONE);

        assertNumeric("investment_market_bar_daily.open_price", 38, 18);
        assertNumeric("investment_market_bar_intraday.quote_volume", 38, 18);
        assertNumeric("investment_contract_spec.contract_multiplier", 38, 18);
        assertNumeric("investment_contract_spec.maker_fee_rate", 24, 12);
        assertNumeric("investment_position_tier.maintenance_margin_rate", 24, 12);
        assertNumeric("investment_funding_rate.funding_rate", 24, 12);

        assertThat(columnMetadata("investment_ingest_cursor.price_type").nullable()).isFalse();
        assertThat(columnMetadata("investment_ingest_cursor.interval_code").nullable()).isFalse();
    }

    @Test
    void flywayDefinesPrimaryAndInternalForeignKeys() throws SQLException {
        assertPrimaryKey("investment_venue", "id");
        assertPrimaryKey("investment_data_source", "id");
        assertPrimaryKey("investment_instrument", "id");
        assertPrimaryKey("investment_instrument_source", "id");
        assertPrimaryKey("investment_contract_spec", "instrument_id");
        assertPrimaryKey("investment_position_tier", "id");
        assertPrimaryKey("investment_market_bar_daily",
                "source_id", "instrument_id", "price_type", "bar_date", "revision");
        assertPrimaryKey("investment_market_bar_intraday",
                "source_id", "instrument_id", "price_type", "interval_code", "open_time", "revision");
        assertPrimaryKey("investment_contract_quote_latest", "source_id", "instrument_id");
        assertPrimaryKey("investment_funding_rate", "source_id", "instrument_id", "funding_time", "revision");
        assertPrimaryKey("investment_workspace", "id");
        assertPrimaryKey("investment_ingest_cursor", "id");
        assertPrimaryKey("investment_job", "id");
        assertPrimaryKey("investment_data_quality_issue", "id");
        assertPrimaryKey("investment_watchlist", "id");
        assertPrimaryKey("investment_watchlist_item", "id");
        assertPrimaryKey("investment_research_report", "id");
        assertPrimaryKey("investment_report_evidence", "id");

        assertForeignKey("investment_data_source", "venue_id", "investment_venue");
        assertForeignKey("investment_instrument", "venue_id", "investment_venue");
        assertForeignKey("investment_instrument_source", "instrument_id", "investment_instrument");
        assertForeignKey("investment_instrument_source", "source_id", "investment_data_source");
        assertForeignKey("investment_contract_spec", "instrument_id", "investment_instrument");
        assertForeignKey("investment_contract_spec", "source_id", "investment_data_source");
        assertForeignKey("investment_position_tier", "source_id", "investment_data_source");
        assertForeignKey("investment_position_tier", "instrument_id", "investment_instrument");
        assertForeignKey("investment_market_bar_daily", "source_id", "investment_data_source");
        assertForeignKey("investment_market_bar_daily", "instrument_id", "investment_instrument");
        assertForeignKey("investment_market_bar_intraday", "source_id", "investment_data_source");
        assertForeignKey("investment_market_bar_intraday", "instrument_id", "investment_instrument");
        assertForeignKey("investment_contract_quote_latest", "source_id", "investment_data_source");
        assertForeignKey("investment_contract_quote_latest", "instrument_id", "investment_instrument");
        assertForeignKey("investment_funding_rate", "source_id", "investment_data_source");
        assertForeignKey("investment_funding_rate", "instrument_id", "investment_instrument");
        assertForeignKey("investment_ingest_cursor", "source_id", "investment_data_source");
        assertForeignKey("investment_ingest_cursor", "instrument_id", "investment_instrument");
        assertForeignKey("investment_job", "workspace_id", "investment_workspace");
        assertForeignKey("investment_data_quality_issue", "job_id", "investment_job");
        assertForeignKey("investment_data_quality_issue", "source_id", "investment_data_source");
        assertForeignKey("investment_data_quality_issue", "instrument_id", "investment_instrument");
        assertForeignKey("investment_watchlist", "workspace_id", "investment_workspace");
        assertForeignKey("investment_watchlist_item", "watchlist_id", "investment_watchlist");
        assertForeignKey("investment_watchlist_item", "instrument_id", "investment_instrument");
        assertForeignKey("investment_research_report", "workspace_id", "investment_workspace");
        assertForeignKey("investment_research_report", "instrument_id", "investment_instrument");
        assertForeignKey("investment_report_evidence", "report_id", "investment_research_report");
        assertForeignKey("investment_report_evidence", "source_id", "investment_data_source");
        assertForeignKey("investment_report_evidence", "instrument_id", "investment_instrument");
    }

    @Test
    void flywayDefinesBusinessUniquenessAndPlannerAwareIndexes() {
        assertConstraintColumns("uk_investment_venue_code", "code");
        assertConstraintColumns("uk_investment_data_source_code", "code");
        assertConstraintColumns("uk_investment_instrument_business", "venue_id", "product_type", "symbol");
        assertConstraintColumns("uk_investment_instrument_source_external",
                "source_id", "external_product_type", "external_symbol");
        assertConstraintColumns("uk_investment_instrument_source_mapping", "instrument_id", "source_id");
        assertConstraintColumns("uk_investment_position_tier_snapshot",
                "source_id", "instrument_id", "observed_at", "tier_level");
        assertConstraintColumns("uk_investment_market_bar_daily_slot",
                "source_id", "instrument_id", "price_type", "bar_date", "revision_slot");
        assertConstraintColumns("uk_investment_market_bar_intraday_slot",
                "source_id", "instrument_id", "price_type", "interval_code", "open_time", "revision_slot");
        assertConstraintColumns("uk_investment_funding_rate_slot",
                "source_id", "instrument_id", "funding_time", "revision_slot");
        assertConstraintColumns("uk_investment_ingest_cursor_dimension",
                "source_id", "instrument_id", "data_type", "price_type", "interval_code");
        assertConstraintColumns("uk_investment_job_idempotency", "idempotency_key");
        assertConstraintColumns("uk_investment_workspace_owner_name", "owner_user_id", "name");
        assertConstraintColumns("uk_investment_watchlist_workspace_name", "workspace_id", "name");
        assertConstraintColumns("uk_investment_watchlist_item_instrument", "watchlist_id", "instrument_id");

        assertIndexColumns("idx_investment_data_source_venue_status", "venue_id:ASC", "status:ASC");
        assertIndexColumns("idx_investment_instrument_product_status_quote",
                "product_type:ASC", "status:ASC", "quote_asset:ASC");
        assertIndexColumns("idx_investment_position_tier_instrument_observed",
                "instrument_id:ASC", "observed_at:DESC", "tier_level:ASC");
        assertIndexColumns("idx_investment_market_bar_daily_lookup",
                "instrument_id:ASC", "price_type:ASC", "revision_slot:ASC", "bar_date:DESC");
        assertIndexColumns("idx_investment_market_bar_intraday_lookup",
                "instrument_id:ASC", "price_type:ASC", "interval_code:ASC", "revision_slot:ASC", "open_time:DESC");
        assertIndexColumns("idx_investment_funding_rate_lookup",
                "instrument_id:ASC", "revision_slot:ASC", "funding_time:DESC");
        assertIndexColumns("idx_investment_job_dispatch",
                "status:ASC", "available_at:ASC", "priority:ASC", "id:ASC");
        assertIndexColumns("idx_investment_job_workspace_created", "workspace_id:ASC", "created_at:DESC");
        assertIndexColumns("idx_investment_data_quality_resolution",
                "resolution_status:ASC", "severity:ASC", "created_at:ASC");
        assertIndexColumns("idx_investment_data_quality_instrument_time",
                "instrument_id:ASC", "point_time:DESC");
        assertIndexColumns("idx_investment_research_report_workspace_created",
                "workspace_id:ASC", "created_at:DESC");
        assertIndexColumns("idx_investment_research_report_instrument_as_of",
                "instrument_id:ASC", "data_as_of:DESC");
        assertIndexColumns("idx_investment_report_evidence_report",
                "report_id:ASC", "created_at:ASC");
    }

    @Test
    void flywayDefinesNamedValueRevisionAndStateChecks() {
        assertCheckContains("chk_investment_contract_spec_positive", "quantity_step", "contract_multiplier");
        assertCheckContains("chk_investment_position_tier_values", "tier_level", "max_leverage");

        for (String table : List.of("investment_market_bar_daily", "investment_market_bar_intraday")) {
            assertCheckContains("chk_" + table + "_ohlc", "low_price", "open_price", "close_price", "high_price");
            assertCheckContains("chk_" + table + "_positive", "open_price", "base_volume");
            assertCheckContains("chk_" + table + "_revision", "revision", "revision_slot");
            assertCheckContains("chk_" + table + "_revision_state", "revision_slot", "valid_to");
        }

        assertCheckContains("chk_investment_funding_rate_revision", "revision", "revision_slot");
        assertCheckContains("chk_investment_funding_rate_revision_state", "revision_slot", "valid_to");
        assertCheckContains("chk_investment_job_state", "status", "attempts", "max_attempts");
        assertCheckContains("chk_investment_job_lease_state", "claim_token", "lease_expires_at");
        assertCheckContains("chk_investment_research_report_state", "status", "report_version");
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
                .as(table + " primary key")
                .containsExactly(expectedColumns);
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
        assertThat(references).as(table + "." + column + " foreign key")
                .containsExactly(referencedTable);
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
