package top.egon.mario.im;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ImCoreSchemaMigrationTests {

    private static final Path IM_CORE_MIGRATION = Path.of(
            "src/main/resources/db/migration/V30__create_im_core_schema.sql");
    private static final Path IM_PLATFORM_FRIENDSHIP_MIGRATION = Path.of(
            "src/main/resources/db/migration/V46__create_im_platform_friendship_schema.sql");
    private static final Path IM_SURFACE_INVITATION_MIGRATION = Path.of(
            "src/main/resources/db/migration/V47__create_im_surface_invitation_schema.sql");
    private static final Path IM_POSTGRESQL_INDEX_MIGRATION = Path.of(
            "src/main/resources/db/postgresql/R__create_im_core_postgresql_indexes.sql");

    private static final List<String> LEGACY_TABLES = List.of(
            "im_read_state",
            "im_message",
            "im_conversation_member",
            "im_conversation",
            "im_group",
            "im_channel"
    );

    private static final List<String> REPLACEMENT_TABLES = List.of(
            "im_channel",
            "im_group",
            "im_dm_pair",
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

    private static final List<String> PLATFORM_TABLES = List.of(
            "im_friendship",
            "im_contact",
            "im_surface_invitation"
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
                    "id", "context_type", "context_id", "channel_key", "name", "owner_user_id", "visibility",
                    "join_policy", "status", "announcement", "main_conversation_id", "member_count",
                    "last_active_at")),
            Map.entry("im_group", List.of(
                    "id", "channel_id", "context_type", "context_id", "group_key", "name", "owner_user_id",
                    "join_policy", "status", "announcement", "conversation_id", "member_count", "last_active_at")),
            Map.entry("im_dm_pair", List.of(
                    "id", "user_lo_id", "user_hi_id", "conversation_id", "frozen")),
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

    @Test
    void migrationDropsLegacyTablesBeforeCreatingReplacementTables() throws IOException {
        String sql = readMigration();

        int lastDropPosition = -1;
        for (String table : LEGACY_TABLES) {
            int dropPosition = sql.indexOf("DROP TABLE IF EXISTS " + table + ";");
            assertThat(dropPosition).as(table + " drop order").isGreaterThan(lastDropPosition);
            lastDropPosition = dropPosition;
        }

        int firstCreatePosition = Integer.MAX_VALUE;
        for (String table : REPLACEMENT_TABLES) {
            int createPosition = sql.indexOf("CREATE TABLE " + table);
            assertThat(createPosition).as(table + " create statement").isGreaterThan(lastDropPosition);
            firstCreatePosition = Math.min(firstCreatePosition, createPosition);
        }
        assertThat(lastDropPosition).isLessThan(firstCreatePosition);
        assertThat(sql).doesNotContain("CREATE TABLE im_read_state");
    }

    @Test
    void migrationDefinesReplacementTablesWithRequiredColumnsAndAuditing() throws IOException {
        String sql = readMigration();

        for (String table : REPLACEMENT_TABLES) {
            String tableDefinition = tableDefinition(sql, table);
            assertThat(tableDefinition).as(table + " definition").isNotBlank();

            for (String column : REQUIRED_COLUMNS.get(table)) {
                assertThat(tableDefinition).as(table + "." + column).containsPattern("\\n\\s+" + column + "\\s");
            }
            for (String column : AUDIT_COLUMNS) {
                assertThat(tableDefinition).as(table + "." + column).containsPattern("\\n\\s+" + column + "\\s");
            }
        }
    }

    @Test
    void migrationDefinesPostgreSqlIndexesForCoreImWorkloads() throws IOException {
        String sql = readMigration();
        String postgresqlSql = readPostgresqlIndexMigration();

        assertThat(sql).doesNotContain(" WHERE ");

        assertThat(postgresqlSql).contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_im_channel_global_context_key");
        assertThat(postgresqlSql).contains("ON im_channel (context_type, channel_key)");
        assertThat(postgresqlSql).contains("WHERE context_id IS NULL");

        assertThat(postgresqlSql).contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_im_group_standalone_context_key");
        assertThat(postgresqlSql).contains("ON im_group (context_type, context_id, group_key)");
        assertThat(postgresqlSql).contains("WHERE channel_id IS NULL AND context_id IS NOT NULL");

        assertThat(postgresqlSql).contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_im_group_standalone_global_key");
        assertThat(postgresqlSql).contains("ON im_group (context_type, group_key)");
        assertThat(postgresqlSql).contains("WHERE channel_id IS NULL AND context_id IS NULL");

        assertThat(sql).contains("CREATE INDEX idx_im_outbox_dispatch");
        assertThat(sql).contains("ON im_outbox (status, available_at)");

        assertThat(postgresqlSql).contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_im_join_request_pending_surface_user");
        assertThat(postgresqlSql).contains("ON im_join_request (surface_type, surface_id, user_id)");
        assertThat(postgresqlSql).contains("WHERE status = 'PENDING' AND deleted = FALSE");

        assertThat(postgresqlSql).contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_im_message_client_msg");
        assertThat(postgresqlSql).contains("ON im_message (conversation_id, sender_user_id, client_msg_id)");
        assertThat(postgresqlSql).contains("WHERE client_msg_id IS NOT NULL");

        assertThat(postgresqlSql).contains("CREATE INDEX IF NOT EXISTS idx_im_global_mute_active_lookup");
        assertThat(postgresqlSql).contains("ON im_global_mute (user_id, scope_type, scope_id, expires_at)");
        assertThat(postgresqlSql).contains("WHERE status = 'ACTIVE' AND deleted = FALSE");

        assertThat(postgresqlSql).contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_im_dm_block_active");
        assertThat(postgresqlSql).contains("ON im_dm_block (blocker_user_id, blocked_user_id)");
        assertThat(postgresqlSql).contains("WHERE status = 'ACTIVE' AND deleted = FALSE");

        assertThat(sql).contains("CREATE INDEX idx_im_conversation_last_active_at");
        assertThat(sql).contains("ON im_conversation (last_active_at)");
    }

    @Test
    void migrationAllowsGlobalContextRowsAndEnforcesDmPairOrdering() throws IOException {
        String sql = readMigration();

        assertThat(tableDefinition(sql, "im_channel"))
                .containsPattern("\\n\\s+context_id\\s+BIGINT,");
        assertThat(tableDefinition(sql, "im_group"))
                .containsPattern("\\n\\s+context_id\\s+BIGINT,");
        assertThat(tableDefinition(sql, "im_conversation"))
                .containsPattern("\\n\\s+context_id\\s+BIGINT,");
        assertThat(tableDefinition(sql, "im_dm_pair"))
                .contains("CONSTRAINT chk_im_dm_pair_ordered CHECK (user_lo_id < user_hi_id)");
    }

    @Test
    void platformMigrationAddsFriendshipAndDirectedContactWithoutRewritingCoreTables() throws IOException {
        String sql = readPlatformFriendshipMigration();

        assertThat(sql).doesNotContain("DROP TABLE");
        assertThat(tableDefinition(sql, "im_friendship"))
                .containsPattern("\\n\\s+user_lo_id\\s+BIGINT NOT NULL")
                .containsPattern("\\n\\s+user_hi_id\\s+BIGINT NOT NULL")
                .containsPattern("\\n\\s+requester_user_id\\s+BIGINT NOT NULL")
                .containsPattern("\\n\\s+status\\s+VARCHAR\\(32\\) NOT NULL")
                .contains("CONSTRAINT uk_im_friendship_users UNIQUE (user_lo_id, user_hi_id)")
                .contains("CONSTRAINT chk_im_friendship_ordered CHECK (user_lo_id < user_hi_id)");
        assertThat(tableDefinition(sql, "im_contact"))
                .containsPattern("\\n\\s+friendship_id\\s+BIGINT NOT NULL")
                .containsPattern("\\n\\s+owner_user_id\\s+BIGINT NOT NULL")
                .containsPattern("\\n\\s+contact_user_id\\s+BIGINT NOT NULL")
                .contains("CONSTRAINT uk_im_contact_owner_user UNIQUE (owner_user_id, contact_user_id)")
                .contains("CONSTRAINT chk_im_contact_not_self CHECK (owner_user_id <> contact_user_id)");
        for (String table : List.of("im_friendship", "im_contact")) {
            String definition = tableDefinition(sql, table);
            for (String column : AUDIT_COLUMNS) {
                assertThat(definition).as(table + "." + column).containsPattern("\\n\\s+" + column + "\\s");
            }
        }
        assertThat(sql).contains("CREATE INDEX idx_im_friendship_lo_status");
        assertThat(sql).contains("CREATE INDEX idx_im_friendship_hi_status");
        assertThat(sql).contains("CREATE INDEX idx_im_contact_owner_status");
    }

    @Test
    void surfaceInvitationMigrationAddsDurableChannelAndGroupInvitations() throws IOException {
        String sql = readSurfaceInvitationMigration();
        String definition = tableDefinition(sql, "im_surface_invitation");

        assertThat(sql).doesNotContain("DROP TABLE");
        assertThat(definition)
                .containsPattern("\\n\\s+surface_type\\s+VARCHAR\\(32\\) NOT NULL")
                .containsPattern("\\n\\s+surface_id\\s+BIGINT NOT NULL")
                .containsPattern("\\n\\s+inviter_user_id\\s+BIGINT NOT NULL")
                .containsPattern("\\n\\s+invitee_user_id\\s+BIGINT NOT NULL")
                .containsPattern("\\n\\s+status\\s+VARCHAR\\(32\\) NOT NULL")
                .contains("CONSTRAINT uk_im_surface_invitation_target UNIQUE "
                        + "(surface_type, surface_id, invitee_user_id)")
                .contains("CONSTRAINT chk_im_surface_invitation_type CHECK "
                        + "(surface_type IN ('CHANNEL', 'GROUP'))")
                .contains("CONSTRAINT chk_im_surface_invitation_not_self CHECK "
                        + "(inviter_user_id <> invitee_user_id)");
        for (String column : AUDIT_COLUMNS) {
            assertThat(definition).as("im_surface_invitation." + column)
                    .containsPattern("\\n\\s+" + column + "\\s");
        }
        assertThat(sql).contains("CREATE INDEX idx_im_surface_invitation_invitee_status")
                .contains("ON im_surface_invitation (invitee_user_id, status, created_at)")
                .contains("CREATE INDEX idx_im_surface_invitation_surface_status")
                .contains("ON im_surface_invitation (surface_type, surface_id, status)");
    }

    @Test
    void migrationClasspathRunsOnH2AndCreatesReplacementTables() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:im_core_schema_%s;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
                .formatted(UUID.randomUUID()));
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        for (String table : REPLACEMENT_TABLES) {
            Integer tableCount = jdbcTemplate.queryForObject("""
                    select count(*)
                    from information_schema.tables
                    where table_schema = 'public'
                      and table_name = ?
                    """, Integer.class, table);
            assertThat(tableCount).as(table + " exists after Flyway migration").isEqualTo(1);
        }
        for (String table : PLATFORM_TABLES) {
            Integer tableCount = jdbcTemplate.queryForObject("""
                    select count(*)
                    from information_schema.tables
                    where table_schema = 'public'
                      and table_name = ?
                    """, Integer.class, table);
            assertThat(tableCount).as(table + " exists after Flyway migration").isEqualTo(1);
        }
    }

    private static String readMigration() throws IOException {
        assertThat(Files.exists(IM_CORE_MIGRATION)).isTrue();
        return Files.readString(IM_CORE_MIGRATION);
    }

    private static String readPostgresqlIndexMigration() throws IOException {
        assertThat(Files.exists(IM_POSTGRESQL_INDEX_MIGRATION)).isTrue();
        return Files.readString(IM_POSTGRESQL_INDEX_MIGRATION);
    }

    private static String readPlatformFriendshipMigration() throws IOException {
        assertThat(Files.exists(IM_PLATFORM_FRIENDSHIP_MIGRATION)).isTrue();
        return Files.readString(IM_PLATFORM_FRIENDSHIP_MIGRATION);
    }

    private static String readSurfaceInvitationMigration() throws IOException {
        assertThat(Files.exists(IM_SURFACE_INVITATION_MIGRATION)).isTrue();
        return Files.readString(IM_SURFACE_INVITATION_MIGRATION);
    }

    private static String tableDefinition(String sql, String table) {
        Pattern pattern = Pattern.compile("CREATE TABLE " + table + " \\((.*?)\\);", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(sql);
        assertThat(matcher.find()).as(table + " create statement").isTrue();
        return matcher.group(1);
    }
}
