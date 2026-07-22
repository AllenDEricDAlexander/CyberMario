package top.egon.mario.agent.externalim;

import org.flywaydb.core.Flyway;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExternalImPostgresContractIT {

    private DisposablePostgresSchema database;

    @BeforeAll
    void migrateDisposableSchema() {
        database = DisposablePostgresSchema.create();
    }

    @AfterAll
    void dropDisposableSchema() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void migrationCreatesTablesColumnsAndOrderedScopeIndex() {
        assertThat(tables()).contains(
                "agent_memory_space",
                "agent_external_chat_binding",
                "agent_external_chat_event",
                "agent_chat_guard_audit");
        assertThat(columns("agent_memory_message")).contains(
                "memory_domain", "memory_space_id", "source_platform",
                "source_conversation_type", "audience_key", "external_event_id",
                "external_sender_id", "observed_only");
        assertThat(indexDefinition("idx_agent_long_term_memory_owner_scope_key"))
                .contains("(user_id, scope_type, scope_key)");
    }

    @Test
    void sourceEventAndSpaceScopedLongTermMemoryAreUnique() {
        String eventInsert = """
                insert into agent_external_chat_event (
                    platform, connector_id, external_event_id,
                    normalized_message_json, processing_status, reply_status,
                    available_at, received_at, created_at, updated_at
                ) values (
                    'TELEGRAM', 'main', 'update-1',
                    '{}', 'RECEIVED', 'NOT_REQUIRED',
                    current_timestamp, current_timestamp,
                    current_timestamp, current_timestamp
                )
                """;
        database.jdbc().update(eventInsert);
        assertThatThrownBy(() -> database.jdbc().update(eventInsert))
                .hasRootCauseInstanceOf(SQLException.class);

        String memoryInsert = """
                insert into agent_long_term_memory (
                    user_id, scope_type, scope_key, memory_space_id,
                    content_markdown, content_chars, status,
                    created_at, updated_at
                ) values (
                    8, 'IM_SHARED', 'space-1', 'space-1',
                    '', 0, 'ACTIVE', current_timestamp, current_timestamp
                )
                """;
        database.jdbc().update(memoryInsert);
        assertThatThrownBy(() -> database.jdbc().update(memoryInsert))
                .hasRootCauseInstanceOf(SQLException.class);
    }

    private List<String> tables() {
        return database.jdbc().queryForList("""
                select table_name
                from information_schema.tables
                where table_schema = current_schema()
                """, String.class);
    }

    private List<String> columns(String table) {
        return database.jdbc().queryForList("""
                select column_name
                from information_schema.columns
                where table_schema = current_schema() and table_name = ?
                """, String.class, table);
    }

    private String indexDefinition(String indexName) {
        String definition = database.jdbc().queryForObject("""
                select indexdef
                from pg_indexes
                where schemaname = current_schema() and indexname = ?
                """, String.class, indexName);
        return definition == null ? "" : definition.toLowerCase(Locale.ROOT)
                .replace("\"", "").replaceAll("\\s+", " ");
    }

    private static final class DisposablePostgresSchema implements AutoCloseable {

        private static final String REQUIRED_ENV = """
                ExternalImPostgresContractIT requires
                EXTERNAL_IM_POSTGRES_TEST_URL,
                EXTERNAL_IM_POSTGRES_TEST_USERNAME and
                EXTERNAL_IM_POSTGRES_TEST_PASSWORD;
                use only a disposable PostgreSQL database.
                """;

        private final DriverManagerDataSource adminDataSource;
        private final JdbcTemplate jdbc;
        private final String schema;

        private DisposablePostgresSchema(DriverManagerDataSource adminDataSource,
                                         DriverManagerDataSource schemaDataSource,
                                         String schema) {
            this.adminDataSource = adminDataSource;
            this.jdbc = new JdbcTemplate(schemaDataSource);
            this.schema = schema;
        }

        static DisposablePostgresSchema create() {
            String url = System.getenv("EXTERNAL_IM_POSTGRES_TEST_URL");
            String username = System.getenv("EXTERNAL_IM_POSTGRES_TEST_USERNAME");
            String password = System.getenv("EXTERNAL_IM_POSTGRES_TEST_PASSWORD");
            if (isBlank(url) || isBlank(username) || isBlank(password)) {
                fail(REQUIRED_ENV);
            }

            DriverManagerDataSource admin = dataSource(url, username, password);
            try (Connection ignored = admin.getConnection()) {
                // Connection is checked before creating the disposable schema.
            } catch (SQLException error) {
                fail("Unable to connect to EXTERNAL_IM_POSTGRES_TEST_URL: "
                        + rootMessage(error), error);
            }

            String schema = "external_im_"
                    + UUID.randomUUID().toString().replace("-", "");
            new JdbcTemplate(admin).execute("create schema " + schema);
            DriverManagerDataSource scoped = dataSource(
                    withCurrentSchema(url, schema), username, password);
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
                flyway.validate();
                return new DisposablePostgresSchema(admin, scoped, schema);
            } catch (RuntimeException error) {
                new JdbcTemplate(admin).execute(
                        "drop schema if exists " + schema + " cascade");
                throw error;
            }
        }

        JdbcTemplate jdbc() {
            return jdbc;
        }

        @Override
        public void close() {
            new JdbcTemplate(adminDataSource).execute(
                    "drop schema if exists " + schema + " cascade");
        }

        private static DriverManagerDataSource dataSource(
                String url, String username, String password) {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.postgresql.Driver");
            dataSource.setUrl(url);
            dataSource.setUsername(username);
            dataSource.setPassword(password);
            return dataSource;
        }

        private static String withCurrentSchema(String url, String schema) {
            return url + (url.contains("?") ? "&" : "?")
                    + "currentSchema=" + schema;
        }

        private static boolean isBlank(String value) {
            return value == null || value.isBlank();
        }

        private static String rootMessage(Throwable throwable) {
            Throwable current = throwable;
            while (current.getCause() != null) {
                current = current.getCause();
            }
            return current.getMessage() == null
                    ? current.getClass().getSimpleName()
                    : current.getMessage();
        }
    }
}
