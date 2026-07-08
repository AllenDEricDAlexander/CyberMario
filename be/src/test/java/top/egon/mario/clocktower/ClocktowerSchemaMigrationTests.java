package top.egon.mario.clocktower;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClocktowerSchemaMigrationTests {

    private static final Path ROOM_IM_GAME_REFACTOR_MIGRATION = Path.of(
            "src/main/resources/db/migration/V26__create_room_im_clocktower_refactor_schema.sql");
    private static final Path ACTOR_AGENT_FOUNDATION_MIGRATION = Path.of(
            "src/main/resources/db/migration/V32__clocktower_actor_agent_foundation.sql");

    @Test
    void roomImGameRefactorMigrationCreatesGenericRoomTables() throws IOException {
        assertThat(Files.exists(ROOM_IM_GAME_REFACTOR_MIGRATION)).isTrue();

        String sql = Files.readString(ROOM_IM_GAME_REFACTOR_MIGRATION);

        assertThat(sql).contains("CREATE TABLE room_space");
        assertThat(sql).contains("CREATE TABLE room_member");
        assertThat(sql).contains("CREATE TABLE room_invitation");
        assertThat(sql).contains("CREATE TABLE room_ban");
        assertThat(sql).contains("active_status");
        assertThat(sql).contains("target_seat_no");
        assertThat(sql).contains("uk_room_member_room_user_active");
        assertThat(sql).contains("uk_room_member_room_seat_active");
        assertThat(sql).contains("uk_room_invitation_room_target_seat_active");
        assertThat(sql).contains("idx_room_member_active_status");
        assertThat(sql).contains("idx_room_invitation_room_status");
        assertThat(sql).doesNotContain("idx_room_member_seat_reservation");
        assertThat(sql).doesNotContain("idx_room_invitation_active_target_seat");
        assertThat(sql).doesNotContain("CONSTRAINT uk_room_member_room_user UNIQUE (room_id, user_id)");
        assertThat(sql).doesNotContain("CONSTRAINT uk_room_member_room_seat UNIQUE (room_id, seat_no)");
    }

    @Test
    void roomImGameRefactorMigrationCreatesGenericImTables() throws IOException {
        assertThat(Files.exists(ROOM_IM_GAME_REFACTOR_MIGRATION)).isTrue();

        String sql = Files.readString(ROOM_IM_GAME_REFACTOR_MIGRATION);

        assertThat(sql).contains("CREATE TABLE im_channel");
        assertThat(sql).contains("CREATE TABLE im_group");
        assertThat(sql).contains("CREATE TABLE im_conversation");
        assertThat(sql).contains("CREATE TABLE im_conversation_member");
        assertThat(sql).contains("CREATE TABLE im_message");
        assertThat(sql).contains("CREATE TABLE im_read_state");
        assertThat(sql).contains("message_seq");
        assertThat(sql).contains("group_id BIGINT NOT NULL");
        assertThat(sql).contains("uk_im_conversation_group_scope_type_participant");
        assertThat(sql).contains("group_id, scope_type, scope_id, conversation_type, participant_key");
        assertThat(sql).contains("uk_im_message_conversation_seq");
        assertThat(sql).contains("uk_im_read_state_conversation_user");
        assertThat(sql).contains("idx_im_conversation_scope_status");
        assertThat(sql).doesNotContain("idx_im_message_conversation_seq");
        assertThat(sql).doesNotContain("uk_im_conversation_context_scope_participant");
        assertThat(sql).doesNotContain("uk_im_read_state_conversation_member UNIQUE");
    }

    @Test
    void roomImGameRefactorMigrationEnforcesActiveReservationsButKeepsTerminalHistory() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:room_im_game_refactor_active_keys_%s;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
                .formatted(UUID.randomUUID()));
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        jdbcTemplate.update("""
                insert into room_member (room_id, user_id, member_type, status, active_status, seat_no, display_name)
                values (1, 10, 'PLAYER', 'ACTIVE', true, 1, 'Alice')
                """);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into room_member (room_id, user_id, member_type, status, active_status, seat_no, display_name)
                values (1, 10, 'PLAYER', 'ACTIVE', true, 2, 'Alice Again')
                """)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into room_member (room_id, user_id, member_type, status, active_status, seat_no, display_name)
                values (1, 11, 'PLAYER', 'ACTIVE', true, 1, 'Bob')
                """)).isInstanceOf(Exception.class);
        jdbcTemplate.update("""
                insert into room_member (room_id, user_id, member_type, status, active_status, seat_no, display_name)
                values (1, 10, 'PLAYER', 'LEFT', null, 1, 'Alice History 1')
                """);
        jdbcTemplate.update("""
                insert into room_member (room_id, user_id, member_type, status, active_status, seat_no, display_name)
                values (1, 10, 'PLAYER', 'LEFT', null, 1, 'Alice History 2')
                """);

        jdbcTemplate.update("""
                insert into room_invitation (room_id, inviter_user_id, invitation_code, status, active_status, target_seat_no)
                values (1, 100, 'INV-A', 'PENDING', true, 3)
                """);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into room_invitation (room_id, inviter_user_id, invitation_code, status, active_status, target_seat_no)
                values (1, 100, 'INV-B', 'PENDING', true, 3)
                """)).isInstanceOf(Exception.class);
        jdbcTemplate.update("""
                insert into room_invitation (room_id, inviter_user_id, invitation_code, status, active_status, target_seat_no)
                values (1, 100, 'INV-C', 'EXPIRED', null, 3)
                """);
        jdbcTemplate.update("""
                insert into room_invitation (room_id, inviter_user_id, invitation_code, status, active_status, target_seat_no)
                values (1, 100, 'INV-D', 'DECLINED', null, 3)
                """);

        Integer terminalMemberRows = jdbcTemplate.queryForObject("""
                select count(*)
                from room_member
                where room_id = 1
                  and user_id = 10
                  and active_status is null
                """, Integer.class);
        Integer terminalInvitationRows = jdbcTemplate.queryForObject("""
                select count(*)
                from room_invitation
                where room_id = 1
                  and target_seat_no = 3
                  and active_status is null
                """, Integer.class);

        assertThat(terminalMemberRows).isEqualTo(2);
        assertThat(terminalInvitationRows).isEqualTo(2);
    }

    @Test
    void roomImGameRefactorMigrationCreatesClocktowerGameTables() throws IOException {
        assertThat(Files.exists(ROOM_IM_GAME_REFACTOR_MIGRATION)).isTrue();

        String sql = Files.readString(ROOM_IM_GAME_REFACTOR_MIGRATION);

        assertThat(sql).contains("CREATE TABLE clocktower_room_profile");
        assertThat(sql).contains("CREATE TABLE clocktower_room_seat");
        assertThat(sql).contains("CREATE TABLE clocktower_game");
        assertThat(sql).contains("CREATE TABLE clocktower_game_seat");
        assertThat(sql).contains("CREATE TABLE clocktower_game_event");
        assertThat(sql).contains("game_no");
        assertThat(sql).contains("current_game_id");
        assertThat(sql).contains("board_snapshot_json");
        assertThat(sql).contains("idx_clocktower_game_room_status");
        assertThat(sql).contains("idx_clocktower_game_event_game_phase");
        assertThat(sql).doesNotContain("idx_clocktower_game_room_no");
        assertThat(sql).doesNotContain("idx_clocktower_game_event_game_seq");
    }

    @Test
    void actorAgentFoundationMigrationCreatesActorAgentTablesAndSeatColumns() throws IOException {
        assertThat(Files.exists(ACTOR_AGENT_FOUNDATION_MIGRATION)).isTrue();

        String sql = Files.readString(ACTOR_AGENT_FOUNDATION_MIGRATION);
        String lowerSql = sql.toLowerCase();

        assertThat(sql).contains("CREATE TABLE clocktower_actor");
        assertThat(sql).contains("CREATE TABLE clocktower_agent_profile");
        assertThat(sql).contains("CREATE TABLE clocktower_agent_instance");
        assertThat(sql).contains("ALTER TABLE clocktower_room_seat");
        assertThat(sql).contains("ALTER TABLE clocktower_game_seat");
        assertThat(sql).contains("ADD COLUMN actor_id BIGINT");
        assertThat(sql).contains("ADD COLUMN actor_type VARCHAR(32) NOT NULL DEFAULT 'HUMAN'");
        assertThat(sql).contains("ADD COLUMN agent_instance_id BIGINT");
        assertThat(sql).contains("created_by BIGINT");
        assertThat(sql).contains("updated_by BIGINT");
        assertThat(sql).contains("version BIGINT NOT NULL DEFAULT 0");
        assertThat(sql).contains("CONSTRAINT uk_clocktower_agent_profile_name UNIQUE (name, deleted)");
        assertThat(sql).contains("CONSTRAINT uk_clocktower_agent_instance_actor UNIQUE (actor_id, deleted)");
        assertThat(sql).contains("idx_clocktower_room_seat_actor");
        assertThat(sql).contains("idx_clocktower_room_seat_agent");
        assertThat(sql).contains("idx_clocktower_game_seat_actor");
        assertThat(sql).contains("idx_clocktower_game_seat_agent");
        assertThat(sql).contains("('balanced', 'Agent {n}', 'NORMAL', 50, 50, 50, 50)");
        assertThat(sql).contains("('quiet', 'Agent {n}', 'QUIET', 25, 40, 35, 40)");
        assertThat(sql).contains("('aggressive', 'Agent {n}', 'AGGRESSIVE', 65, 60, 75, 60)");
        assertThat(sql).contains("('careful', 'Agent {n}', 'CAREFUL', 45, 35, 35, 25)");
        assertThat(lowerSql).doesNotContain(" check ");
        assertThat(lowerSql).doesNotContain("where deleted = false");
        assertThat(sql).doesNotContain("uk_clocktower_actor_user");
    }

    @Test
    void actorAgentFoundationMigrationAppliesAndSupportsHumanAndAgentSeats() {
        JdbcTemplate jdbcTemplate = migratedJdbcTemplate("clocktower_actor_agent_foundation_%s"
                .formatted(UUID.randomUUID()));

        Integer profileCount = jdbcTemplate.queryForObject("""
                select count(*)
                from clocktower_agent_profile
                where name in ('balanced', 'quiet', 'aggressive', 'careful')
                """, Integer.class);
        assertThat(profileCount).isEqualTo(4);

        jdbcTemplate.update("""
                insert into clocktower_actor (id, actor_type, user_id, display_name)
                values (92001, 'AGENT', null, 'Agent 1')
                """);
        jdbcTemplate.update("""
                insert into clocktower_agent_instance (id, room_id, profile_id, actor_id, status, auto_mode)
                values (93001, 91001,
                        (select id from clocktower_agent_profile where name = 'balanced'),
                        92001, 'ACTIVE', 'FULL_AUTO')
                """);
        jdbcTemplate.update("""
                insert into clocktower_room_seat
                    (id, room_id, seat_no, user_id, display_name, role_code, status, actor_type)
                values
                    (94001, 91001, 1, 101, 'Human Player', 'EMPATH', 'OCCUPIED', 'HUMAN')
                """);
        jdbcTemplate.update("""
                insert into clocktower_room_seat
                    (id, room_id, seat_no, user_id, display_name, role_code, status,
                     actor_id, actor_type, agent_instance_id)
                values
                    (94002, 91001, 2, null, 'Agent 1', 'CHEF', 'OCCUPIED',
                     92001, 'AGENT', 93001)
                """);
        jdbcTemplate.update("""
                insert into clocktower_game
                    (id, room_id, game_no, script_code, status, phase, board_snapshot_json)
                values
                    (95001, 91001, 1, 'TROUBLE_BREWING', 'RUNNING', 'FIRST_NIGHT', '{}')
                """);
        jdbcTemplate.update("""
                insert into clocktower_game_seat
                    (id, game_id, room_seat_id, seat_no, user_id, display_name, role_code,
                     status, actor_type)
                values
                    (96001, 95001, 94001, 1, 101, 'Human Player', 'EMPATH',
                     'ACTIVE', 'HUMAN')
                """);
        jdbcTemplate.update("""
                insert into clocktower_game_seat
                    (id, game_id, room_seat_id, seat_no, user_id, display_name, role_code,
                     status, actor_id, actor_type, agent_instance_id)
                values
                    (96002, 95001, 94002, 2, null, 'Agent 1', 'CHEF',
                     'ACTIVE', 92001, 'AGENT', 93001)
                """);

        List<Map<String, Object>> roomSeats = jdbcTemplate.queryForList("""
                select seat_no, user_id, actor_id, actor_type, agent_instance_id
                from clocktower_room_seat
                where room_id = 91001
                order by seat_no
                """);
        assertThat(roomSeats).hasSize(2);
        assertThat(roomSeats.get(0).get("actor_type")).isEqualTo("HUMAN");
        assertThat(longValue(roomSeats.get(0), "user_id")).isEqualTo(101L);
        assertThat(roomSeats.get(0).get("actor_id")).isNull();
        assertThat(roomSeats.get(0).get("agent_instance_id")).isNull();
        assertThat(roomSeats.get(1).get("actor_type")).isEqualTo("AGENT");
        assertThat(roomSeats.get(1).get("user_id")).isNull();
        assertThat(longValue(roomSeats.get(1), "actor_id")).isEqualTo(92001L);
        assertThat(longValue(roomSeats.get(1), "agent_instance_id")).isEqualTo(93001L);

        List<Map<String, Object>> gameSeats = jdbcTemplate.queryForList("""
                select seat_no, user_id, actor_id, actor_type, agent_instance_id
                from clocktower_game_seat
                where game_id = 95001
                order by seat_no
                """);
        assertThat(gameSeats).hasSize(2);
        assertThat(gameSeats.get(0).get("actor_type")).isEqualTo("HUMAN");
        assertThat(longValue(gameSeats.get(0), "user_id")).isEqualTo(101L);
        assertThat(gameSeats.get(1).get("actor_type")).isEqualTo("AGENT");
        assertThat(gameSeats.get(1).get("user_id")).isNull();
        assertThat(longValue(gameSeats.get(1), "actor_id")).isEqualTo(92001L);
        assertThat(longValue(gameSeats.get(1), "agent_instance_id")).isEqualTo(93001L);

        String nullable = jdbcTemplate.queryForObject("""
                select is_nullable
                from information_schema.columns
                where table_name = 'im_conversation_member'
                  and column_name = 'user_id'
                """, String.class);
        assertThat(nullable).isEqualTo("NO");
    }

    @Test
    void migrationCreatesCoreClocktowerTablesAndSeedsThreeScripts() throws IOException {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V18__create_clocktower_core_schema.sql"));

        assertThat(sql).contains("CREATE TABLE clocktower_script");
        assertThat(sql).contains("CREATE TABLE clocktower_role");
        assertThat(sql).contains("CREATE TABLE clocktower_night_order");
        assertThat(sql).contains("CREATE TABLE clocktower_jinx_rule");
        assertThat(sql).contains("CREATE TABLE clocktower_room");
        assertThat(sql).contains("CREATE TABLE clocktower_seat");
        assertThat(sql).contains("CREATE TABLE clocktower_event");
        assertThat(sql).contains("CREATE TABLE clocktower_grimoire_entry");
        assertThat(sql).contains("CREATE TABLE clocktower_status_marker");
        assertThat(sql).contains("CREATE TABLE clocktower_nomination");
        assertThat(sql).contains("CREATE TABLE clocktower_vote");
        assertThat(sql).contains("CREATE TABLE clocktower_board_config");
        assertThat(sql).contains("CREATE TABLE clocktower_board_role");
        assertThat(sql).contains("CREATE TABLE clocktower_storyteller_task");
        assertThat(sql).contains("TROUBLE_BREWING");
        assertThat(sql).contains("BAD_MOON_RISING");
        assertThat(sql).contains("SECTS_AND_VIOLETS");
        assertThat(sql).contains("idx_clocktower_event_room_seq");
        assertThat(sql).contains("uk_clocktower_room_code");
    }

    @Test
    void clocktowerRuleDataSourceFilesUseReviewedCsvHeaders() throws IOException {
        Path rolesPath = Path.of("../docs/clocktower/rule-data/clocktower-base-scripts-roles.csv");
        Path nightOrderPath = Path.of("../docs/clocktower/rule-data/clocktower-base-scripts-night-order.csv");

        assertThat(Files.exists(rolesPath)).isTrue();
        assertThat(Files.exists(nightOrderPath)).isTrue();

        List<String> roleLines = Files.readAllLines(rolesPath);
        List<String> nightOrderLines = Files.readAllLines(nightOrderPath);

        assertThat(roleLines).isNotEmpty();
        assertThat(nightOrderLines).isNotEmpty();
        assertThat(roleLines.getFirst()).isEqualTo(
                "scriptCode,roleCode,name,roleType,alignment,abilityText,firstNightOrder,otherNightOrder,firstNightReminder,otherNightReminder,sourceUrl");
        assertThat(nightOrderLines.getFirst()).isEqualTo(
                "scriptCode,nightType,orderNo,roleCode,reminderText");
    }

    @Test
    void clocktowerRuleDataSourceFilesContainReviewedChineseBaseScriptData() throws IOException {
        Path rolesPath = Path.of("../docs/clocktower/rule-data/clocktower-base-scripts-roles.csv");
        Path nightOrderPath = Path.of("../docs/clocktower/rule-data/clocktower-base-scripts-night-order.csv");

        String roles = Files.readString(rolesPath);
        String nightOrder = Files.readString(nightOrderPath);

        assertThat(roles.lines()).hasSize(73);
        assertThat(nightOrder.lines()).hasSize(75);
        assertThat(roles).contains("TROUBLE_BREWING,WASHERWOMAN,洗衣妇");
        assertThat(roles).contains("BAD_MOON_RISING,GRANDMOTHER,祖母");
        assertThat(roles).contains("SECTS_AND_VIOLETS,CLOCKMAKER,钟表匠");
        assertThat(roles).contains("CHEF,厨师");
        assertThat(roles).contains("IMP,小恶魔");
        assertThat(nightOrder).contains("TROUBLE_BREWING,FIRST_NIGHT,1,POISONER");
        assertThat(nightOrder).contains("BAD_MOON_RISING");
        assertThat(nightOrder).contains("SECTS_AND_VIOLETS");
        assertThat(nightOrder).contains("让投毒者选择一名玩家");
    }

    @Test
    void completeClocktowerRuleDataMigrationCoversThreeBaseScripts() throws IOException {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20__complete_clocktower_rule_data.sql"));

        assertThat(sql).contains("TROUBLE_BREWING");
        assertThat(sql).contains("BAD_MOON_RISING");
        assertThat(sql).contains("SECTS_AND_VIOLETS");
        assertThat(sql).contains("WASHERWOMAN");
        assertThat(sql).contains("CHEF");
        assertThat(sql).contains("IMP");
        assertThat(sql).contains("FIRST_NIGHT");
        assertThat(sql).contains("OTHER_NIGHT");
        assertThat(sql).contains("clocktower_role");
        assertThat(sql).contains("clocktower_night_order");
        assertThat(sql).contains("ALTER TABLE clocktower_role");
        assertThat(sql).doesNotContain("DROP TABLE");
        assertThat(sql).doesNotContain("TRUNCATE");
    }

    @Test
    void rulingMigrationAddsPublicLifeAndRulingTable() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V21__create_clocktower_ruling_system.sql");

        String sql = Files.readString(migration);

        assertThat(sql).contains("ALTER TABLE clocktower_seat");
        assertThat(sql).contains("public_life_status");
        assertThat(sql).contains("CREATE TABLE clocktower_ruling");
        assertThat(sql).contains("snapshot_json");
    }

    @Test
    void boardValidMigrationAddsQueryableValidFlag() throws IOException {
        Path migration = Path.of("src/main/resources/db/migration/V22__add_clocktower_board_valid.sql");

        String sql = Files.readString(migration);

        assertThat(sql).contains("ALTER TABLE clocktower_board_config");
        assertThat(sql).contains("ADD COLUMN valid BOOLEAN NOT NULL DEFAULT FALSE");
    }

    @Test
    void completeClocktowerRuleDataMigrationAppliesReviewedBaseScriptRows() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:clocktower_rule_data_migration_%s;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
                .formatted(UUID.randomUUID()));
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        Integer roleCount = jdbcTemplate.queryForObject("""
                select count(*)
                from clocktower_role
                where script_code in ('TROUBLE_BREWING', 'BAD_MOON_RISING', 'SECTS_AND_VIOLETS')
                  and deleted = false
                """, Integer.class);
        Integer nightOrderCount = jdbcTemplate.queryForObject("""
                select count(*)
                from clocktower_night_order
                where script_code in ('TROUBLE_BREWING', 'BAD_MOON_RISING', 'SECTS_AND_VIOLETS')
                  and deleted = false
                """, Integer.class);

        assertThat(roleCount).isEqualTo(72);
        assertThat(nightOrderCount).isEqualTo(74);
        assertThat(jdbcTemplate.queryForObject(
                "select name from clocktower_role where role_code = 'CHEF'", String.class)).isEqualTo("厨师");
        assertThat(jdbcTemplate.queryForObject(
                "select role_type from clocktower_role where role_code = 'CHEF'", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select alignment from clocktower_role where role_code = 'IMP'", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                select night_type
                from clocktower_night_order
                where script_code = 'TROUBLE_BREWING'
                  and role_code = 'POISONER'
                  and night_type = 1
                  and order_no = 1
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void migrationRemovesOldBroadRoomPermissionGrant() throws IOException {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V19__disable_old_clocktower_room_wildcard_permission.sql"));

        assertThat(sql).contains("api:clocktower:rooms:*");
        assertThat(sql).contains("DELETE");
        assertThat(sql).contains("FROM sys_role_permission");
        assertThat(sql).contains("permission_version = permission_version + 1");
        assertThat(sql).contains("UPDATE sys_permission");
        assertThat(sql).contains("status = 0");
    }

    @Test
    void oldClocktowerRbacResourceRetirementMigrationDisablesLegacyPermissionsOnly() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:clocktower_rbac_resource_retirement_%s;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
                .formatted(UUID.randomUUID()));
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("26"))
                .load()
                .migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("""
                insert into sys_role (id, role_code, role_name, status, sort_no, built_in, description,
                                      created_at, updated_at, version, deleted, permission_version, managed,
                                      owner_app, source_type, source_key, sync_hash, last_synced_at)
                values (9100, 'CLOCKTOWER_PLAYER', 'Clocktower Player', 1, 40, false, 'test role',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, false, 0, true,
                        'clocktower', 'PROVIDER', 'clocktower:CLOCKTOWER_PLAYER', 'old', CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                insert into sys_permission (id, perm_code, perm_name, perm_type, status, sort_no, description,
                                            created_at, updated_at, version, deleted, managed, owner_app,
                                            source_type, source_key, sync_hash, last_synced_at, last_seen_at)
                values (9200, 'api:clocktower:rooms:player:view', 'Old room view', 3, 1, 0, 'old permission',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, false, true, 'clocktower',
                        'PROVIDER', 'clocktower:api:clocktower:rooms:player:view', 'old', CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                insert into sys_permission (id, perm_code, perm_name, perm_type, status, sort_no, description,
                                            created_at, updated_at, version, deleted, managed, owner_app,
                                            source_type, source_key, sync_hash, last_synced_at, last_seen_at)
                values (9201, 'api:clocktower:game:read', 'Game read', 3, 1, 0, 'canonical permission',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, false, true, 'clocktower',
                        'PROVIDER', 'clocktower:api:clocktower:game:read', 'new', CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                insert into sys_role_permission (id, role_id, permission_id, granted_at)
                values (9300, 9100, 9200, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                insert into sys_role_permission (id, role_id, permission_id, granted_at)
                values (9301, 9100, 9201, CURRENT_TIMESTAMP)
                """);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        Integer oldGrantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission
                where role_id = 9100
                  and permission_id = 9200
                """, Integer.class);
        Integer canonicalGrantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission
                where role_id = 9100
                  and permission_id = 9201
                """, Integer.class);

        assertThat(oldGrantCount).isZero();
        assertThat(canonicalGrantCount).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select permission_version from sys_role where id = 9100", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "select status from sys_permission where id = 9200", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select version from sys_permission where id = 9200", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "select status from sys_permission where id = 9201", Integer.class)).isEqualTo(1);
    }

    private JdbcTemplate migratedJdbcTemplate(String databaseName) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("""
                jdbc:h2:mem:%s;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1
                """.formatted(databaseName).trim());
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        return new JdbcTemplate(dataSource);
    }

    private static Long longValue(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value == null ? null : ((Number) value).longValue();
    }
}
