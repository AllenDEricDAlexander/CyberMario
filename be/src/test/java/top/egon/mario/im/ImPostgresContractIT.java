package top.egon.mario.im;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class ImPostgresContractIT {

    private static final String REQUIRED_ENV_MESSAGE = """
            ImPostgresContractIT requires IM_POSTGRES_TEST_URL, IM_POSTGRES_TEST_USERNAME, \
            and IM_POSTGRES_TEST_PASSWORD; a disposable PostgreSQL DB is required.""";

    private static final Path IM_CORE_MIGRATION = Path.of(
            "src/main/resources/db/migration/V30__create_im_core_schema.sql");
    private static final Path IM_PLATFORM_FRIENDSHIP_MIGRATION = Path.of(
            "src/main/resources/db/migration/V46__create_im_platform_friendship_schema.sql");
    private static final Path IM_SURFACE_INVITATION_MIGRATION = Path.of(
            "src/main/resources/db/migration/V48__create_im_surface_invitation_schema.sql");
    private static final Path IM_SURFACE_JOIN_KEY_MIGRATION = Path.of(
            "src/main/resources/db/migration/V49__add_im_surface_join_keys.sql");
    private static final Path IM_POSTGRESQL_INDEX_MIGRATION = Path.of(
            "src/main/resources/db/postgresql/R__create_im_core_postgresql_indexes.sql");

    private static final List<String> IM_TABLES = List.of(
            "im_channel",
            "im_group",
            "im_dm_pair",
            "im_friendship",
            "im_contact",
            "im_surface_invitation",
            "im_membership",
            "im_join_request",
            "im_conversation",
            "im_conversation_member",
            "im_message",
            "im_outbox",
            "im_inbox",
            "im_global_mute",
            "im_dm_block",
            "im_ban",
            "im_ws_ticket"
    );

    private static final List<String> AUDIT_COLUMNS = List.of(
            "metadata_json",
            "created_at",
            "updated_at",
            "created_by",
            "updated_by",
            "version",
            "deleted"
    );

    private static final Map<String, List<String>> REQUIRED_COLUMNS = Map.ofEntries(
            Map.entry("im_channel", List.of(
                    "id", "context_type", "context_id", "channel_key", "join_key", "name", "owner_user_id", "visibility",
                    "join_policy", "status", "announcement", "main_conversation_id", "member_count",
                    "last_active_at")),
            Map.entry("im_group", List.of(
                    "id", "channel_id", "context_type", "context_id", "group_key", "join_key", "name", "owner_user_id",
                    "join_policy", "status", "announcement", "conversation_id", "member_count", "last_active_at")),
            Map.entry("im_dm_pair", List.of(
                    "id", "user_lo_id", "user_hi_id", "conversation_id", "frozen")),
            Map.entry("im_friendship", List.of(
                    "id", "user_lo_id", "user_hi_id", "requester_user_id", "status", "request_message",
                    "decided_by", "decided_at", "decision_reason", "requested_at", "activated_at", "removed_at")),
            Map.entry("im_contact", List.of(
                    "id", "friendship_id", "owner_user_id", "contact_user_id", "remark", "status")),
            Map.entry("im_surface_invitation", List.of(
                    "id", "surface_type", "surface_id", "inviter_user_id", "invitee_user_id", "status",
                    "message", "responded_at")),
            Map.entry("im_membership", List.of(
                    "id", "surface_type", "surface_id", "user_id", "member_role", "status", "muted_until",
                    "joined_at")),
            Map.entry("im_join_request", List.of(
                    "id", "surface_type", "surface_id", "user_id", "status", "decided_by", "decided_at",
                    "decision_reason")),
            Map.entry("im_conversation", List.of(
                    "id", "conversation_type", "owner_surface_type", "owner_surface_id", "context_type",
                    "context_id", "message_seq", "last_message_id", "last_message_at", "last_active_at",
                    "status")),
            Map.entry("im_conversation_member", List.of(
                    "id", "conversation_id", "user_id", "last_read_seq", "delivery_mode", "muted", "status")),
            Map.entry("im_message", List.of(
                    "id", "conversation_id", "sender_user_id", "message_seq", "client_msg_id", "message_type",
                    "content", "payload_json", "status", "sent_at", "edited_at", "deleted_at")),
            Map.entry("im_outbox", List.of(
                    "id", "conversation_id", "message_id", "message_seq", "event_type", "status", "available_at",
                    "attempts", "last_error")),
            Map.entry("im_inbox", List.of(
                    "id", "user_id", "conversation_id", "message_id", "message_seq", "read")),
            Map.entry("im_global_mute", List.of(
                    "id", "user_id", "scope_type", "scope_id", "expires_at", "reason", "status")),
            Map.entry("im_dm_block", List.of(
                    "id", "blocker_user_id", "blocked_user_id", "status", "reason")),
            Map.entry("im_ban", List.of(
                    "id", "surface_type", "surface_id", "user_id", "actor_user_id", "reason", "expires_at",
                    "status")),
            Map.entry("im_ws_ticket", List.of(
                    "id", "token_hash", "user_id", "roles_json", "expires_at", "consumed_at", "status"))
    );

    private static final Map<String, List<String>> JSONB_COLUMNS = Map.ofEntries(
            Map.entry("im_channel", List.of("metadata_json")),
            Map.entry("im_group", List.of("metadata_json")),
            Map.entry("im_dm_pair", List.of("metadata_json")),
            Map.entry("im_friendship", List.of("metadata_json")),
            Map.entry("im_contact", List.of("metadata_json")),
            Map.entry("im_surface_invitation", List.of("metadata_json")),
            Map.entry("im_membership", List.of("metadata_json")),
            Map.entry("im_join_request", List.of("metadata_json")),
            Map.entry("im_conversation", List.of("metadata_json")),
            Map.entry("im_conversation_member", List.of("metadata_json")),
            Map.entry("im_message", List.of("metadata_json", "payload_json")),
            Map.entry("im_outbox", List.of("metadata_json")),
            Map.entry("im_inbox", List.of("metadata_json")),
            Map.entry("im_global_mute", List.of("metadata_json")),
            Map.entry("im_dm_block", List.of("metadata_json")),
            Map.entry("im_ban", List.of("metadata_json")),
            Map.entry("im_ws_ticket", List.of("metadata_json", "roles_json"))
    );

    private static final List<String> DATA_CLEANUP_ORDER = List.of(
            "im_inbox",
            "im_outbox",
            "im_message",
            "im_conversation_member",
            "im_contact",
            "im_friendship",
            "im_surface_invitation",
            "im_dm_pair",
            "im_join_request",
            "im_membership",
            "im_global_mute",
            "im_dm_block",
            "im_ban",
            "im_ws_ticket",
            "im_group",
            "im_channel",
            "im_conversation"
    );

    private static final AtomicLong UNIQUE = new AtomicLong(System.currentTimeMillis());

    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate jdbcTemplate;
    private static String schemaName;
    private static String migrationLocation;

    @BeforeEach
    void setUp() throws IOException {
        ensureDatabase();
        migrateSchema();
        deleteOnlyImRows();
    }

    @Test
    void migrateSchemaCreatesImTablesAndRequiredColumns() {
        Integer v30Count = jdbcTemplate.queryForObject("""
                select count(*)
                from %s.flyway_schema_history
                where version = '30'
                  and script = 'V30__create_im_core_schema.sql'
                """.formatted(schemaName), Integer.class);
        assertThat(v30Count).as("V30 IM schema migration must be applied").isEqualTo(1);

        Integer v41Count = jdbcTemplate.queryForObject("""
                select count(*)
                from %s.flyway_schema_history
                where version = '41'
                  and script = 'V46__create_im_platform_friendship_schema.sql'
                """.formatted(schemaName), Integer.class);
        assertThat(v41Count).as("V41 platform friendship migration must be applied").isEqualTo(1);

        Integer v42Count = jdbcTemplate.queryForObject("""
                select count(*)
                from %s.flyway_schema_history
                where version = '42'
                  and script = 'V48__create_im_surface_invitation_schema.sql'
                """.formatted(schemaName), Integer.class);
        assertThat(v42Count).as("V42 surface invitation migration must be applied").isEqualTo(1);

        Integer v49Count = jdbcTemplate.queryForObject("""
                select count(*)
                from %s.flyway_schema_history
                where version = '49'
                  and script = 'V49__add_im_surface_join_keys.sql'
                """.formatted(schemaName), Integer.class);
        assertThat(v49Count).as("V49 surface join-key migration must be applied").isEqualTo(1);

        Integer repeatableCount = jdbcTemplate.queryForObject("""
                select count(*)
                from %s.flyway_schema_history
                where version is null
                  and script = 'R__create_im_core_postgresql_indexes.sql'
                """.formatted(schemaName), Integer.class);
        assertThat(repeatableCount).as("IM PostgreSQL repeatable index migration must be applied").isEqualTo(1);

        for (String table : IM_TABLES) {
            Integer tableCount = jdbcTemplate.queryForObject("""
                    select count(*)
                    from information_schema.tables
                    where table_schema = ?
                      and table_name = ?
                    """, Integer.class, schemaName, table);
            assertThat(tableCount).as(table + " table exists").isEqualTo(1);

            for (String column : REQUIRED_COLUMNS.get(table)) {
                assertColumnExists(table, column);
            }
        }
    }

    @Test
    void auditColumnsExistOnEveryImTable() {
        for (String table : IM_TABLES) {
            for (String column : AUDIT_COLUMNS) {
                assertColumnExists(table, column);
            }
        }
    }

    @Test
    void jsonColumnsUsePostgreSqlJsonbType() {
        for (Map.Entry<String, List<String>> entry : JSONB_COLUMNS.entrySet()) {
            for (String column : entry.getValue()) {
                String udtName = jdbcTemplate.queryForObject("""
                        select udt_name
                        from information_schema.columns
                        where table_schema = ?
                          and table_name = ?
                          and column_name = ?
                        """, String.class, schemaName, entry.getKey(), column);
                assertThat(udtName).as(entry.getKey() + "." + column + " PostgreSQL type").isEqualTo("jsonb");
            }
        }
    }

    @Test
    void postgreSqlIndexesMatchImContract() {
        assertIndex("im_channel", "uk_im_channel_global_context_key")
                .unique()
                .columns("context_type", "channel_key")
                .predicateContains("context_id", "is null");
        assertIndex("im_channel", "uk_im_channel_join_key")
                .unique()
                .columns("join_key");
        assertIndex("im_group", "uk_im_group_standalone_context_key")
                .unique()
                .columns("context_type", "context_id", "group_key")
                .predicateContains("channel_id", "is null", "context_id", "is not null");
        assertIndex("im_group", "uk_im_group_standalone_global_key")
                .unique()
                .columns("context_type", "group_key")
                .predicateContains("channel_id", "is null", "context_id", "is null");
        assertIndex("im_group", "uk_im_group_join_key")
                .unique()
                .columns("join_key");
        assertIndex("im_message", "uk_im_message_client_msg")
                .unique()
                .columns("conversation_id", "sender_user_id", "client_msg_id")
                .predicateContains("client_msg_id", "is not null");
        assertIndex("im_outbox", "idx_im_outbox_dispatch")
                .columns("status", "available_at");
        assertIndex("im_join_request", "uk_im_join_request_pending_surface_user")
                .unique()
                .columns("surface_type", "surface_id", "user_id")
                .predicateContains("status", "pending", "deleted", "false");
        assertIndex("im_global_mute", "idx_im_global_mute_active_lookup")
                .columns("user_id", "scope_type", "scope_id", "expires_at")
                .predicateContains("status", "active", "deleted", "false");
        assertIndex("im_dm_block", "uk_im_dm_block_active")
                .unique()
                .columns("blocker_user_id", "blocked_user_id")
                .predicateContains("status", "active", "deleted", "false");
        assertIndex("im_friendship", "uk_im_friendship_users")
                .unique()
                .columns("user_lo_id", "user_hi_id");
        assertIndex("im_friendship", "idx_im_friendship_lo_status")
                .columns("user_lo_id", "status");
        assertIndex("im_friendship", "idx_im_friendship_hi_status")
                .columns("user_hi_id", "status");
        assertIndex("im_contact", "uk_im_contact_owner_user")
                .unique()
                .columns("owner_user_id", "contact_user_id");
        assertIndex("im_contact", "idx_im_contact_owner_status")
                .columns("owner_user_id", "status");
        assertIndex("im_surface_invitation", "uk_im_surface_invitation_target")
                .unique()
                .columns("surface_type", "surface_id", "invitee_user_id");
        assertIndex("im_surface_invitation", "idx_im_surface_invitation_invitee_status")
                .columns("invitee_user_id", "status", "created_at");
        assertIndex("im_surface_invitation", "idx_im_surface_invitation_surface_status")
                .columns("surface_type", "surface_id", "status");
    }

    @Test
    void outboxClaimUsesForUpdateSkipLockedWithoutOverlappingRows() throws SQLException {
        long context = nextUnique();
        jdbcTemplate.update("""
                insert into %s.im_outbox
                    (conversation_id, message_id, message_seq, event_type, status, available_at)
                values (?, ?, 1, 'IM_MESSAGE_CREATED', 'PENDING', now() - interval '1 minute'),
                       (?, ?, 2, 'IM_MESSAGE_CREATED', 'PENDING', now() - interval '1 minute')
                """.formatted(schemaName), context, context + 10, context, context + 11);

        String claimSql = """
                select id
                from %s.im_outbox
                where status = 'PENDING'
                  and available_at <= now()
                order by available_at, id
                limit 1
                for update skip locked
                """.formatted(schemaName);

        try (Connection txA = dataSource.getConnection(); Connection txB = dataSource.getConnection()) {
            txA.setAutoCommit(false);
            txB.setAutoCommit(false);
            Long txAClaimed = claimOneOutboxRow(txA, claimSql);
            Long txBClaimed = claimOneOutboxRow(txB, claimSql);

            assertThat(txAClaimed).as("transaction A claims one pending outbox row").isNotNull();
            assertThat(txBClaimed).as("transaction B claims a different unlocked pending outbox row").isNotNull();
            assertThat(txBClaimed).as("transaction B must not see transaction A's locked outbox row")
                    .isNotEqualTo(txAClaimed);

            txB.rollback();
            txA.rollback();
        }
    }

    @Test
    void conversationRowLockSerializesMessageSequenceIncrements() throws Exception {
        long context = nextUnique();
        Long conversationId = jdbcTemplate.queryForObject("""
                insert into %s.im_conversation
                    (conversation_type, owner_surface_type, owner_surface_id, context_type, context_id, status)
                values ('GROUP', 'GROUP', ?, 'IM_CONTRACT', ?, 'ACTIVE')
                returning id
                """.formatted(schemaName), Long.class, context, context);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Long> first = executor.submit(() -> insertNextMessageAfterLock(start, conversationId, context + 1));
            Future<Long> second = executor.submit(() -> insertNextMessageAfterLock(start, conversationId, context + 2));

            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .as("both concurrent inserts receive serialized message sequences")
                    .containsExactlyInAnyOrder(1L, 2L);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS))
                    .as("conversation sequence executor shuts down")
                    .isTrue();
        }

        Long finalSequence = jdbcTemplate.queryForObject("""
                select message_seq
                from %s.im_conversation
                where id = ?
                """.formatted(schemaName), Long.class, conversationId);
        assertThat(finalSequence).as("final conversation message_seq").isEqualTo(2L);

        List<Long> messageSequences = jdbcTemplate.queryForList("""
                select message_seq
                from %s.im_message
                where conversation_id = ?
                order by message_seq
                """.formatted(schemaName), Long.class, conversationId);
        assertThat(messageSequences).as("inserted message sequences have no gap").containsExactly(1L, 2L);
    }

    private static synchronized void ensureDatabase() {
        if (dataSource != null) {
            return;
        }

        String url = System.getenv("IM_POSTGRES_TEST_URL");
        String username = System.getenv("IM_POSTGRES_TEST_USERNAME");
        String password = System.getenv("IM_POSTGRES_TEST_PASSWORD");
        if (isBlank(url) || isBlank(username) || isBlank(password)) {
            fail(REQUIRED_ENV_MESSAGE);
        }

        DriverManagerDataSource configuredDataSource = new DriverManagerDataSource();
        configuredDataSource.setDriverClassName("org.postgresql.Driver");
        configuredDataSource.setUrl(url);
        configuredDataSource.setUsername(username);
        configuredDataSource.setPassword(password);

        try (Connection ignored = configuredDataSource.getConnection()) {
            // Connection preflight keeps credential and disposable-DB failures explicit.
        } catch (SQLException ex) {
            fail("Unable to connect to the disposable PostgreSQL DB using IM_POSTGRES_TEST_URL, "
                    + "IM_POSTGRES_TEST_USERNAME, and IM_POSTGRES_TEST_PASSWORD: " + rootMessage(ex), ex);
        }

        dataSource = configuredDataSource;
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    private static synchronized void migrateSchema() throws IOException {
        if (schemaName == null) {
            MigrationSelection selection = migrateApplicationSchemaOrFallback();
            schemaName = selection.schemaName();
            migrationLocation = selection.location();
            return;
        }

        runFlyway(schemaName, migrationLocation);
    }

    private static MigrationSelection migrateApplicationSchemaOrFallback() throws IOException {
        String applicationSchema = newSchemaName("im_contract_app");
        try {
            createSchema(applicationSchema);
            runFlyway(applicationSchema, "classpath:db/migration,classpath:db/postgresql");
            return new MigrationSelection(applicationSchema, "classpath:db/migration,classpath:db/postgresql");
        } catch (FlywayException ex) {
            if (!isKnownNonImPostgreSqlMigrationFailure(ex)) {
                fail("Full project PostgreSQL Flyway migration failed before IM contract assertions: "
                        + rootMessage(ex), ex);
            }

            String fallbackSchema = newSchemaName("im_contract");
            createSchema(fallbackSchema);
            String fallbackLocation = createImOnlyFlywayLocation();
            System.err.println("Full project PostgreSQL Flyway migration failed; using IM-only fallback for "
                    + "ImPostgresContractIT. Failure: " + rootMessage(ex));
            runFlyway(fallbackSchema, fallbackLocation);
            return new MigrationSelection(fallbackSchema, fallbackLocation);
        }
    }

    private static void runFlyway(String schema, String location) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations(location.split(","))
                .schemas(schema)
                .defaultSchema(schema)
                .cleanDisabled(true)
                .validateOnMigrate(true)
                .load()
                .migrate();
    }

    private static void createSchema(String schema) {
        jdbcTemplate.execute("create schema if not exists " + schema);
    }

    private static String createImOnlyFlywayLocation() throws IOException {
        assertThat(Files.exists(IM_CORE_MIGRATION)).as("V30 IM core migration exists").isTrue();
        assertThat(Files.exists(IM_PLATFORM_FRIENDSHIP_MIGRATION))
                .as("V41 platform friendship migration exists")
                .isTrue();
        assertThat(Files.exists(IM_SURFACE_INVITATION_MIGRATION))
                .as("V42 surface invitation migration exists")
                .isTrue();
        assertThat(Files.exists(IM_SURFACE_JOIN_KEY_MIGRATION))
                .as("V49 surface join-key migration exists")
                .isTrue();
        assertThat(Files.exists(IM_POSTGRESQL_INDEX_MIGRATION)).as("IM PostgreSQL index migration exists").isTrue();

        Path location = Files.createTempDirectory("im-postgres-contract-flyway-");
        Files.copy(IM_CORE_MIGRATION, location.resolve(IM_CORE_MIGRATION.getFileName()));
        Files.copy(IM_PLATFORM_FRIENDSHIP_MIGRATION,
                location.resolve(IM_PLATFORM_FRIENDSHIP_MIGRATION.getFileName()));
        Files.copy(IM_SURFACE_INVITATION_MIGRATION,
                location.resolve(IM_SURFACE_INVITATION_MIGRATION.getFileName()));
        Files.copy(IM_SURFACE_JOIN_KEY_MIGRATION,
                location.resolve(IM_SURFACE_JOIN_KEY_MIGRATION.getFileName()));
        Files.copy(IM_POSTGRESQL_INDEX_MIGRATION, location.resolve(IM_POSTGRESQL_INDEX_MIGRATION.getFileName()));
        return "filesystem:" + location.toAbsolutePath();
    }

    private void deleteOnlyImRows() {
        for (String table : DATA_CLEANUP_ORDER) {
            jdbcTemplate.update("delete from %s.%s".formatted(schemaName, table));
        }
    }

    private void assertColumnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_schema = ?
                  and table_name = ?
                  and column_name = ?
                """, Integer.class, schemaName, table, column);
        assertThat(count).as(table + "." + column + " column exists").isEqualTo(1);
    }

    private IndexContract assertIndex(String table, String indexName) {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap("""
                    select i.indisunique as unique_index,
                           pg_get_indexdef(i.indexrelid) as definition,
                           coalesce(pg_get_expr(i.indpred, i.indrelid), '') as predicate
                    from pg_index i
                    join pg_class idx on idx.oid = i.indexrelid
                    join pg_class tbl on tbl.oid = i.indrelid
                    join pg_namespace ns on ns.oid = tbl.relnamespace
                    where ns.nspname = ?
                      and tbl.relname = ?
                      and idx.relname = ?
                    """, schemaName, table, indexName);
            return new IndexContract(table, indexName, row);
        } catch (EmptyResultDataAccessException ex) {
            fail("Missing PostgreSQL index " + indexName + " on " + table, ex);
            throw ex;
        }
    }

    private Long insertNextMessageAfterLock(CountDownLatch start, Long conversationId, long senderUserId)
            throws Exception {
        start.await(5, TimeUnit.SECONDS);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            try {
                Long currentSequence = selectConversationSequenceForUpdate(connection, conversationId);
                Long nextSequence = currentSequence + 1;

                Thread.sleep(150);
                updateConversationSequence(connection, conversationId, nextSequence);
                insertMessage(connection, conversationId, senderUserId, nextSequence);
                connection.commit();
                return nextSequence;
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            }
        }
    }

    private Long selectConversationSequenceForUpdate(Connection connection, Long conversationId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                select message_seq
                from %s.im_conversation
                where id = ?
                for update
                """.formatted(schemaName))) {
            statement.setLong(1, conversationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).as("conversation row exists for locking").isTrue();
                return resultSet.getLong("message_seq");
            }
        }
    }

    private void updateConversationSequence(Connection connection, Long conversationId, Long nextSequence)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                update %s.im_conversation
                set message_seq = ?,
                    updated_at = now()
                where id = ?
                """.formatted(schemaName))) {
            statement.setLong(1, nextSequence);
            statement.setLong(2, conversationId);
            assertThat(statement.executeUpdate()).as("conversation sequence row update").isEqualTo(1);
        }
    }

    private void insertMessage(Connection connection, Long conversationId, long senderUserId, Long nextSequence)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into %s.im_message
                    (conversation_id, sender_user_id, message_seq, client_msg_id, message_type, content, status)
                values (?, ?, ?, ?, 'TEXT', 'contract message', 'SENT')
                """.formatted(schemaName))) {
            statement.setLong(1, conversationId);
            statement.setLong(2, senderUserId);
            statement.setLong(3, nextSequence);
            statement.setString(4, "contract-" + senderUserId + "-" + nextSequence);
            assertThat(statement.executeUpdate()).as("message insert for sequence " + nextSequence).isEqualTo(1);
        }
    }

    private static Long claimOneOutboxRow(Connection connection, String claimSql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(claimSql)) {
            if (!resultSet.next()) {
                return null;
            }
            return resultSet.getLong("id");
        }
    }

    private static boolean isKnownNonImPostgreSqlMigrationFailure(Throwable throwable) {
        String message = rootMessage(throwable).toLowerCase(Locale.ROOT);
        return (message.contains("extension") || message.contains("operator class") || message.contains("type"))
                && (message.contains("vector")
                || message.contains("hstore")
                || message.contains("uuid-ossp")
                || message.contains("pg_trgm")
                || message.contains("gin_trgm_ops"));
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? throwable.toString() : current.getMessage();
    }

    private static String newSchemaName(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static long nextUnique() {
        return UNIQUE.incrementAndGet();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record MigrationSelection(String schemaName, String location) {
    }

    private static class IndexContract {

        private final String table;
        private final String indexName;
        private final boolean unique;
        private final String definition;
        private final String predicate;

        IndexContract(String table, String indexName, Map<String, Object> row) {
            this.table = table;
            this.indexName = indexName;
            this.unique = Boolean.TRUE.equals(row.get("unique_index"));
            this.definition = normalize(String.valueOf(row.get("definition")));
            this.predicate = normalize(String.valueOf(row.get("predicate")));
        }

        IndexContract unique() {
            assertThat(unique).as(indexName + " on " + table + " is unique").isTrue();
            return this;
        }

        IndexContract columns(String... columns) {
            String expected = "(" + String.join(", ", columns) + ")";
            assertThat(definition).as(indexName + " on " + table + " column order").contains(expected);
            return this;
        }

        IndexContract predicateContains(String... fragments) {
            assertThat(predicate).as(indexName + " on " + table + " predicate").isNotBlank();
            for (String fragment : fragments) {
                assertThat(predicate).as(indexName + " on " + table + " predicate contains " + fragment)
                        .contains(normalize(fragment));
            }
            return this;
        }

        private static String normalize(String value) {
            return value.toLowerCase(Locale.ROOT)
                    .replace("\"", "")
                    .replaceAll("\\s+", " ")
                    .trim();
        }
    }
}
