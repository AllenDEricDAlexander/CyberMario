package top.egon.mario.clocktower.admin.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import top.egon.mario.clocktower.admin.dto.ClocktowerAuditQuery;
import top.egon.mario.clocktower.admin.dto.ClocktowerAuditReportResponse;
import top.egon.mario.clocktower.admin.dto.ClocktowerAuditSummaryResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClocktowerAuditReadRepositoryTests {

    private ClocktowerAuditReadRepository repository;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:clocktower_audit;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        dropTables(jdbcTemplate);
        createTables(jdbcTemplate);
        insertAuditFixture(jdbcTemplate);
        repository = new ClocktowerAuditReadRepository(new NamedParameterJdbcTemplate(dataSource));
    }

    @Test
    void emptyFilterReturnsRoomAndGameMessagesWithStableServerPagination() {
        Page<ClocktowerAuditReportResponse.Message> firstPage = repository.messages(
                new ClocktowerAuditQuery(null, null, null, null), PageRequest.of(0, 2));

        assertThat(firstPage.getTotalElements()).isEqualTo(4);
        assertThat(firstPage.getContent()).extracting(ClocktowerAuditReportResponse.Message::messageId)
                .containsExactly(4420L, 4020L);
    }

    @Test
    void roomFilterIncludesEveryRoomAndGameConversationButNeverAnotherRoom() {
        Page<ClocktowerAuditReportResponse.Message> result = repository.messages(
                new ClocktowerAuditQuery(List.of(2L), null, null, null), PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting(ClocktowerAuditReportResponse.Message::roomId)
                .containsOnly(2L);
        assertThat(result.getContent())
                .extracting(ClocktowerAuditReportResponse.Message::channelKey)
                .containsExactlyInAnyOrder("ROOM", "GAME");
    }

    @Test
    void crossRoomGameAndConversationFiltersUseIntersectionInsteadOfUnion() {
        Page<ClocktowerAuditReportResponse.Message> wrongGame = repository.messages(
                new ClocktowerAuditQuery(List.of(2L), List.of(40L), null, null), PageRequest.of(0, 20));
        Page<ClocktowerAuditReportResponse.Message> wrongConversation = repository.messages(
                new ClocktowerAuditQuery(List.of(2L), null, List.of(402L), null), PageRequest.of(0, 20));

        assertThat(wrongGame).isEmpty();
        assertThat(wrongConversation).isEmpty();
    }

    @Test
    void gameAndConversationFiltersMustMatchTheSameGameScopedConversation() {
        ClocktowerAuditQuery query = new ClocktowerAuditQuery(
                List.of(2L), List.of(20L), List.of(202L), null);

        assertThat(repository.games(query, PageRequest.of(0, 20))).isEmpty();
        assertThat(repository.messages(query, PageRequest.of(0, 20))).isEmpty();
    }

    @Test
    void gameFilterUsesGameChannelContextAndDoesNotConfuseSameNumericRoomId() {
        Page<ClocktowerAuditReportResponse.Message> result = repository.messages(
                new ClocktowerAuditQuery(null, List.of(20L), null, null), PageRequest.of(0, 20));

        assertThat(result.getContent())
                .singleElement()
                .satisfies(message -> {
                    assertThat(message.roomId()).isEqualTo(2L);
                    assertThat(message.gameId()).isEqualTo(20L);
                    assertThat(message.channelKey()).isEqualTo("GAME");
                    assertThat(message.messageId()).isEqualTo(2220L);
                });
    }

    @Test
    void allReportQueriesAndSummaryUseTheSameServerSideScope() {
        ClocktowerAuditQuery query = new ClocktowerAuditQuery(List.of(2L), null, null, "room two");

        ClocktowerAuditSummaryResponse summary = repository.summary(query);

        assertThat(summary.roomCount()).isEqualTo(1);
        assertThat(summary.gameCount()).isEqualTo(1);
        assertThat(summary.eventCount()).isEqualTo(1);
        assertThat(summary.conversationCount()).isEqualTo(2);
        assertThat(summary.messageCount()).isEqualTo(2);
        assertThat(summary.memberCount()).isEqualTo(1);
        assertThat(summary.invitationCount()).isEqualTo(1);
        assertThat(summary.banCount()).isEqualTo(1);
        assertThat(repository.rooms(query, PageRequest.of(0, 20))).hasSize(1);
        assertThat(repository.games(query, PageRequest.of(0, 20))).hasSize(1);
        assertThat(repository.events(query, PageRequest.of(0, 20))).hasSize(1);
        assertThat(repository.conversations(query, PageRequest.of(0, 20))).hasSize(2);
        assertThat(repository.members(query, PageRequest.of(0, 20))).hasSize(1);
        assertThat(repository.invitations(query, PageRequest.of(0, 20))).hasSize(1);
        assertThat(repository.bans(query, PageRequest.of(0, 20))).hasSize(1);
    }

    @Test
    void roomNameIsCaseInsensitiveAndEscapesLikeWildcards() {
        assertThat(repository.rooms(new ClocktowerAuditQuery(null, null, null, "ROOM "),
                PageRequest.of(0, 20))).hasSize(2);
        assertThat(repository.rooms(new ClocktowerAuditQuery(null, null, null, "%"),
                PageRequest.of(0, 20))).isEmpty();
    }

    private void dropTables(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("DROP ALL OBJECTS");
    }

    private void createTables(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE room_space (
                    id BIGINT PRIMARY KEY, context_type VARCHAR(64) NOT NULL,
                    room_code VARCHAR(64) NOT NULL, name VARCHAR(128) NOT NULL,
                    status VARCHAR(32) NOT NULL, visibility VARCHAR(32) NOT NULL, owner_user_id BIGINT,
                    capacity INTEGER NOT NULL, current_member_count INTEGER NOT NULL,
                    last_active_at TIMESTAMP, created_at TIMESTAMP NOT NULL, deleted BOOLEAN NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE clocktower_game (
                    id BIGINT PRIMARY KEY, room_id BIGINT NOT NULL, game_no INTEGER NOT NULL,
                    script_code VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL, phase VARCHAR(32) NOT NULL,
                    day_no INTEGER NOT NULL, night_no INTEGER NOT NULL, started_at TIMESTAMP,
                    ended_at TIMESTAMP, last_active_at TIMESTAMP, created_at TIMESTAMP NOT NULL,
                    deleted BOOLEAN NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE clocktower_game_event (
                    id BIGINT PRIMARY KEY, game_id BIGINT NOT NULL, event_seq BIGINT NOT NULL,
                    event_type VARCHAR(64) NOT NULL, phase VARCHAR(32) NOT NULL, day_no INTEGER NOT NULL,
                    night_no INTEGER NOT NULL, actor_game_seat_id BIGINT, target_game_seat_id BIGINT,
                    visibility VARCHAR(32) NOT NULL, status VARCHAR(32) NOT NULL,
                    occurred_at TIMESTAMP NOT NULL, payload_json VARCHAR(512) NOT NULL,
                    deleted BOOLEAN NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE im_channel (
                    id BIGINT PRIMARY KEY, context_type VARCHAR(64) NOT NULL, context_id BIGINT,
                    channel_key VARCHAR(128) NOT NULL, name VARCHAR(128) NOT NULL, deleted BOOLEAN NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE im_group (
                    id BIGINT PRIMARY KEY, channel_id BIGINT, context_type VARCHAR(64) NOT NULL,
                    group_key VARCHAR(128) NOT NULL, name VARCHAR(128) NOT NULL, deleted BOOLEAN NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE im_conversation (
                    id BIGINT PRIMARY KEY, owner_surface_type VARCHAR(32) NOT NULL, owner_surface_id BIGINT NOT NULL,
                    context_type VARCHAR(64) NOT NULL, conversation_type VARCHAR(32) NOT NULL,
                    status VARCHAR(32) NOT NULL, message_seq BIGINT NOT NULL, last_message_at TIMESTAMP,
                    last_active_at TIMESTAMP, created_at TIMESTAMP NOT NULL, deleted BOOLEAN NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE im_message (
                    id BIGINT PRIMARY KEY, conversation_id BIGINT NOT NULL, sender_user_id BIGINT NOT NULL,
                    message_seq BIGINT NOT NULL, message_type VARCHAR(32) NOT NULL, content VARCHAR(512) NOT NULL,
                    status VARCHAR(32) NOT NULL, sent_at TIMESTAMP NOT NULL, edited_at TIMESTAMP,
                    deleted BOOLEAN NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE room_member (
                    id BIGINT PRIMARY KEY, room_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
                    display_name VARCHAR(128) NOT NULL, member_type VARCHAR(32) NOT NULL,
                    status VARCHAR(32) NOT NULL, active_status BOOLEAN, seat_no INTEGER,
                    joined_at TIMESTAMP NOT NULL, left_at TIMESTAMP, last_active_at TIMESTAMP,
                    created_at TIMESTAMP NOT NULL, deleted BOOLEAN NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE room_invitation (
                    id BIGINT PRIMARY KEY, room_id BIGINT NOT NULL, inviter_user_id BIGINT NOT NULL,
                    invitee_user_id BIGINT, invitation_code VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL,
                    active_status BOOLEAN, target_seat_no INTEGER, expires_at TIMESTAMP,
                    accepted_at TIMESTAMP, created_at TIMESTAMP NOT NULL, deleted BOOLEAN NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE room_ban (
                    id BIGINT PRIMARY KEY, room_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
                    banned_by_user_id BIGINT NOT NULL, reason VARCHAR(512), status VARCHAR(32) NOT NULL,
                    expires_at TIMESTAMP, created_at TIMESTAMP NOT NULL, deleted BOOLEAN NOT NULL
                )
                """);
    }

    private void insertAuditFixture(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("""
                INSERT INTO room_space
                VALUES (2, 'CLOCKTOWER_ROOM', 'ROOM2', 'Room Two', 'ACTIVE', 'PUBLIC', 1, 20, 1,
                        '2026-06-24 08:00:00', '2026-06-20 08:00:00', FALSE)
                """);
        jdbcTemplate.update("""
                INSERT INTO room_space
                VALUES (4, 'CLOCKTOWER_ROOM', 'ROOM4', 'Room Four', 'ACTIVE', 'PUBLIC', 1, 20, 1,
                        '2026-06-24 10:00:00', '2026-06-20 10:00:00', FALSE)
                """);
        jdbcTemplate.update("""
                INSERT INTO clocktower_game
                VALUES (20, 2, 1, 'TROUBLE_BREWING', 'ENDED', 'DAY', 2, 1,
                        '2026-06-24 08:00:00', '2026-06-24 09:00:00', '2026-06-24 09:00:00',
                        '2026-06-24 08:00:00', FALSE)
                """);
        jdbcTemplate.update("""
                INSERT INTO clocktower_game
                VALUES (40, 4, 1, 'TROUBLE_BREWING', 'ENDED', 'DAY', 2, 1,
                        '2026-06-24 10:00:00', '2026-06-24 11:00:00', '2026-06-24 11:00:00',
                        '2026-06-24 10:00:00', FALSE)
                """);
        jdbcTemplate.update("""
                INSERT INTO clocktower_game_event
                VALUES (2000, 20, 1, 'GAME_STARTED', 'DAY', 1, 0, NULL, NULL,
                        'PUBLIC', 'VISIBLE', '2026-06-24 08:00:00', '{}', FALSE)
                """);
        jdbcTemplate.update("""
                INSERT INTO clocktower_game_event
                VALUES (4000, 40, 1, 'GAME_STARTED', 'DAY', 1, 0, NULL, NULL,
                        'PUBLIC', 'VISIBLE', '2026-06-24 10:00:00', '{}', FALSE)
                """);
        insertConversation(jdbcTemplate, 200, 2, "ROOM", 201, 202, 2020, "2026-06-24 08:00:00");
        insertConversation(jdbcTemplate, 220, 20, "GAME", 221, 222, 2220, "2026-06-24 09:00:00");
        insertConversation(jdbcTemplate, 400, 4, "ROOM", 401, 402, 4020, "2026-06-24 10:00:00");
        insertConversation(jdbcTemplate, 440, 40, "GAME", 441, 442, 4420, "2026-06-24 11:00:00");
        jdbcTemplate.update("""
                INSERT INTO im_message
                VALUES (2221, 222, 1, 2, 'TEXT', 'deleted-status', 'DELETED',
                        '2026-06-24 12:00:00', NULL, FALSE)
                """);
        jdbcTemplate.update("""
                INSERT INTO im_message
                VALUES (2222, 222, 1, 3, 'TEXT', 'soft-deleted', 'VISIBLE',
                        '2026-06-24 13:00:00', NULL, TRUE)
                """);
        jdbcTemplate.update("""
                INSERT INTO room_member VALUES
                (200, 2, 2, 'Room Two User', 'MEMBER', 'ACTIVE', TRUE, 1,
                 '2026-06-20 08:00:00', NULL, '2026-06-24 09:00:00', '2026-06-20 08:00:00', FALSE),
                (400, 4, 4, 'Room Four User', 'MEMBER', 'ACTIVE', TRUE, 1,
                 '2026-06-20 10:00:00', NULL, '2026-06-24 11:00:00', '2026-06-20 10:00:00', FALSE)
                """);
        jdbcTemplate.update("""
                INSERT INTO room_invitation VALUES
                (201, 2, 1, 2, 'INVITE2', 'ACCEPTED', FALSE, 1, NULL,
                 '2026-06-20 08:00:00', '2026-06-20 08:00:00', FALSE),
                (401, 4, 1, 4, 'INVITE4', 'ACCEPTED', FALSE, 1, NULL,
                 '2026-06-20 10:00:00', '2026-06-20 10:00:00', FALSE)
                """);
        jdbcTemplate.update("""
                INSERT INTO room_ban VALUES
                (202, 2, 9, 1, 'test', 'EXPIRED', NULL, '2026-06-20 08:00:00', FALSE),
                (402, 4, 9, 1, 'test', 'EXPIRED', NULL, '2026-06-20 10:00:00', FALSE)
                """);
    }

    private void insertConversation(JdbcTemplate jdbcTemplate, long channelId, long contextId, String channelKey,
                                    long groupId, long conversationId, long messageId, String sentAt) {
        jdbcTemplate.update("INSERT INTO im_channel VALUES (?, 'CLOCKTOWER_ROOM', ?, ?, ?, FALSE)",
                channelId, contextId, channelKey, channelKey);
        jdbcTemplate.update("INSERT INTO im_group VALUES (?, ?, 'CLOCKTOWER_ROOM', 'PUBLIC', 'PUBLIC', FALSE)",
                groupId, channelId);
        jdbcTemplate.update("""
                INSERT INTO im_conversation
                VALUES (?, 'GROUP', ?, 'CLOCKTOWER_ROOM', 'GROUP', 'ACTIVE', 1, NULL, ?, ?, FALSE)
                """, conversationId, groupId, sentAt, sentAt);
        jdbcTemplate.update("""
                INSERT INTO im_message
                VALUES (?, ?, 1, 1, 'TEXT', ?, 'VISIBLE', ?, NULL, FALSE)
                """, messageId, conversationId, "message-" + messageId, sentAt);
    }
}
