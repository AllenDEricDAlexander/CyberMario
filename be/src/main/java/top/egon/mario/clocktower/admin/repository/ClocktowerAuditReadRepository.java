package top.egon.mario.clocktower.admin.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import top.egon.mario.clocktower.admin.dto.ClocktowerAuditQuery;
import top.egon.mario.clocktower.admin.dto.ClocktowerAuditReportResponse;
import top.egon.mario.clocktower.admin.dto.ClocktowerAuditSummaryResponse;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Read-only, set-based projection repository for the Clocktower management audit report.
 */
@Repository
@RequiredArgsConstructor
public class ClocktowerAuditReadRepository {

    static final String CONVERSATION_SCOPE_CTE = """
            WITH clocktower_conversation_scope AS (
                SELECT conversation.id AS conversation_id,
                       CASE WHEN channel.channel_key = 'ROOM' THEN channel.context_id ELSE game.room_id END AS room_id,
                       room.name AS room_name,
                       CASE WHEN channel.channel_key = 'GAME' THEN game.id END AS game_id,
                       channel.id AS channel_id,
                       channel.channel_key,
                       channel.name AS channel_name,
                       group_row.id AS group_id,
                       group_row.group_key,
                       group_row.name AS group_name,
                       conversation.conversation_type,
                       conversation.status,
                       conversation.message_seq,
                       conversation.last_message_at,
                       conversation.last_active_at,
                       conversation.created_at
                FROM im_conversation conversation
                JOIN im_group group_row
                  ON conversation.owner_surface_type = 'GROUP'
                 AND conversation.owner_surface_id = group_row.id
                 AND group_row.deleted = FALSE
                JOIN im_channel channel
                  ON group_row.channel_id = channel.id
                 AND channel.deleted = FALSE
                LEFT JOIN clocktower_game game
                  ON channel.channel_key = 'GAME'
                 AND channel.context_id = game.id
                 AND game.deleted = FALSE
                JOIN room_space room
                  ON room.id = CASE
                        WHEN channel.channel_key = 'ROOM' THEN channel.context_id
                        WHEN channel.channel_key = 'GAME' THEN game.room_id
                 END
                 AND room.deleted = FALSE
                 AND room.context_type = 'CLOCKTOWER_ROOM'
                WHERE conversation.deleted = FALSE
                  AND conversation.context_type = 'CLOCKTOWER_ROOM'
                  AND group_row.context_type = 'CLOCKTOWER_ROOM'
                  AND channel.context_type = 'CLOCKTOWER_ROOM'
                  AND channel.channel_key IN ('ROOM', 'GAME')
            )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ClocktowerAuditSummaryResponse summary(ClocktowerAuditQuery query) {
        String sql = CONVERSATION_SCOPE_CTE + """
                SELECT (SELECT COUNT(*) FROM room_space room WHERE %s) AS room_count,
                       (SELECT COUNT(*) FROM clocktower_game game
                         JOIN room_space room ON room.id = game.room_id AND room.deleted = FALSE
                        WHERE %s) AS game_count,
                       (SELECT COUNT(*) FROM clocktower_game_event event
                         JOIN clocktower_game game ON game.id = event.game_id AND game.deleted = FALSE
                         JOIN room_space room ON room.id = game.room_id AND room.deleted = FALSE
                        WHERE %s) AS event_count,
                       (SELECT COUNT(*) FROM clocktower_conversation_scope scope WHERE %s) AS conversation_count,
                       (SELECT COUNT(*) FROM im_message message
                         JOIN clocktower_conversation_scope scope ON scope.conversation_id = message.conversation_id
                        WHERE %s) AS message_count,
                       (SELECT COUNT(*) FROM room_member member
                         JOIN room_space room ON room.id = member.room_id AND room.deleted = FALSE
                        WHERE %s) AS member_count,
                       (SELECT COUNT(*) FROM room_invitation invitation
                         JOIN room_space room ON room.id = invitation.room_id AND room.deleted = FALSE
                        WHERE %s) AS invitation_count,
                       (SELECT COUNT(*) FROM room_ban ban
                         JOIN room_space room ON room.id = ban.room_id AND room.deleted = FALSE
                        WHERE %s) AS ban_count
                """.formatted(
                roomWhere(query), gameWhere(query), eventWhere(query), conversationWhere(query),
                messageWhere(query), roomChildWhere(query, "member"),
                roomChildWhere(query, "invitation"), roomChildWhere(query, "ban"));
        return jdbcTemplate.queryForObject(sql, parameters(query), (rs, rowNum) ->
                new ClocktowerAuditSummaryResponse(
                        rs.getLong("room_count"), rs.getLong("game_count"), rs.getLong("event_count"),
                        rs.getLong("conversation_count"), rs.getLong("message_count"),
                        rs.getLong("member_count"), rs.getLong("invitation_count"), rs.getLong("ban_count")));
    }

    public Page<ClocktowerAuditReportResponse.Room> rooms(ClocktowerAuditQuery query, Pageable pageable) {
        String fromWhere = " FROM room_space room WHERE " + roomWhere(query);
        String dataSql = CONVERSATION_SCOPE_CTE + """
                SELECT room.id AS room_id, room.room_code, room.name AS room_name, room.status, room.visibility,
                       room.owner_user_id, room.capacity, room.current_member_count,
                       room.last_active_at, room.created_at
                """ + fromWhere + " ORDER BY room.created_at DESC, room.id DESC";
        return page(dataSql, CONVERSATION_SCOPE_CTE + "SELECT COUNT(*)" + fromWhere,
                query, pageable, (rs, rowNum) -> new ClocktowerAuditReportResponse.Room(
                        longValue(rs, "room_id"), rs.getString("room_code"), rs.getString("room_name"),
                        rs.getString("status"), rs.getString("visibility"), longValue(rs, "owner_user_id"),
                        rs.getInt("capacity"), rs.getInt("current_member_count"),
                        instant(rs, "last_active_at"), instant(rs, "created_at")));
    }

    public Page<ClocktowerAuditReportResponse.Game> games(ClocktowerAuditQuery query, Pageable pageable) {
        String fromWhere = """
                 FROM clocktower_game game
                 JOIN room_space room ON room.id = game.room_id AND room.deleted = FALSE
                """ + " WHERE " + gameWhere(query);
        String dataSql = CONVERSATION_SCOPE_CTE + """
                SELECT game.id AS game_id, room.id AS room_id, room.name AS room_name, game.game_no,
                       game.script_code, game.status, game.phase, game.day_no, game.night_no,
                       game.started_at, game.ended_at, game.last_active_at, game.created_at
                """ + fromWhere
                + " ORDER BY COALESCE(game.started_at, game.created_at) DESC, game.id DESC";
        return page(dataSql, CONVERSATION_SCOPE_CTE + "SELECT COUNT(*)" + fromWhere,
                query, pageable, (rs, rowNum) -> new ClocktowerAuditReportResponse.Game(
                        longValue(rs, "game_id"), longValue(rs, "room_id"), rs.getString("room_name"),
                        rs.getInt("game_no"), rs.getString("script_code"), rs.getString("status"),
                        rs.getString("phase"), rs.getInt("day_no"), rs.getInt("night_no"),
                        instant(rs, "started_at"), instant(rs, "ended_at"),
                        instant(rs, "last_active_at"), instant(rs, "created_at")));
    }

    public Page<ClocktowerAuditReportResponse.Event> events(ClocktowerAuditQuery query, Pageable pageable) {
        String fromWhere = """
                 FROM clocktower_game_event event
                 JOIN clocktower_game game ON game.id = event.game_id AND game.deleted = FALSE
                 JOIN room_space room ON room.id = game.room_id AND room.deleted = FALSE
                """ + " WHERE " + eventWhere(query);
        String dataSql = CONVERSATION_SCOPE_CTE + """
                SELECT event.id AS event_id, room.id AS room_id, room.name AS room_name, game.id AS game_id,
                       event.event_seq, event.event_type, event.phase, event.day_no, event.night_no,
                       event.actor_game_seat_id, event.target_game_seat_id, event.visibility, event.status,
                       event.occurred_at, event.payload_json
                """ + fromWhere + " ORDER BY event.occurred_at DESC, event.id DESC";
        return page(dataSql, CONVERSATION_SCOPE_CTE + "SELECT COUNT(*)" + fromWhere,
                query, pageable, (rs, rowNum) -> new ClocktowerAuditReportResponse.Event(
                        longValue(rs, "event_id"), longValue(rs, "room_id"), rs.getString("room_name"),
                        longValue(rs, "game_id"), longValue(rs, "event_seq"), rs.getString("event_type"),
                        rs.getString("phase"), rs.getInt("day_no"), rs.getInt("night_no"),
                        longValue(rs, "actor_game_seat_id"), longValue(rs, "target_game_seat_id"),
                        rs.getString("visibility"), rs.getString("status"), instant(rs, "occurred_at"),
                        rs.getString("payload_json")));
    }

    public Page<ClocktowerAuditReportResponse.Conversation> conversations(ClocktowerAuditQuery query,
                                                                           Pageable pageable) {
        String fromWhere = " FROM clocktower_conversation_scope scope WHERE " + conversationWhere(query);
        String dataSql = CONVERSATION_SCOPE_CTE + """
                SELECT scope.conversation_id, scope.room_id, scope.room_name, scope.game_id,
                       scope.channel_id, scope.channel_key, scope.channel_name,
                       scope.group_id, scope.group_key, scope.group_name,
                       scope.conversation_type, scope.status, scope.message_seq,
                       scope.last_message_at, scope.last_active_at, scope.created_at
                """ + fromWhere
                + " ORDER BY COALESCE(scope.last_active_at, scope.created_at) DESC, scope.conversation_id DESC";
        return page(dataSql, CONVERSATION_SCOPE_CTE + "SELECT COUNT(*)" + fromWhere,
                query, pageable, (rs, rowNum) -> new ClocktowerAuditReportResponse.Conversation(
                        longValue(rs, "conversation_id"), longValue(rs, "room_id"), rs.getString("room_name"),
                        longValue(rs, "game_id"), longValue(rs, "channel_id"), rs.getString("channel_key"),
                        rs.getString("channel_name"), longValue(rs, "group_id"), rs.getString("group_key"),
                        rs.getString("group_name"), rs.getString("conversation_type"), rs.getString("status"),
                        longValue(rs, "message_seq"), instant(rs, "last_message_at"),
                        instant(rs, "last_active_at"), instant(rs, "created_at")));
    }

    public Page<ClocktowerAuditReportResponse.Message> messages(ClocktowerAuditQuery query, Pageable pageable) {
        String fromWhere = """
                 FROM im_message message
                 JOIN clocktower_conversation_scope scope ON scope.conversation_id = message.conversation_id
                """ + " WHERE " + messageWhere(query);
        String dataSql = CONVERSATION_SCOPE_CTE + """
                SELECT message.id AS message_id, message.conversation_id,
                       scope.room_id, scope.room_name, scope.game_id, scope.channel_key, scope.group_key,
                       message.sender_user_id, message.message_seq, message.message_type, message.content,
                       message.status, message.sent_at, message.edited_at
                """ + fromWhere + " ORDER BY message.sent_at DESC, message.id DESC";
        return page(dataSql, CONVERSATION_SCOPE_CTE + "SELECT COUNT(*)" + fromWhere,
                query, pageable, (rs, rowNum) -> new ClocktowerAuditReportResponse.Message(
                        longValue(rs, "message_id"), longValue(rs, "conversation_id"),
                        longValue(rs, "room_id"), rs.getString("room_name"), longValue(rs, "game_id"),
                        rs.getString("channel_key"), rs.getString("group_key"),
                        longValue(rs, "sender_user_id"), longValue(rs, "message_seq"),
                        rs.getString("message_type"), rs.getString("content"), rs.getString("status"),
                        instant(rs, "sent_at"), instant(rs, "edited_at")));
    }

    public Page<ClocktowerAuditReportResponse.Member> members(ClocktowerAuditQuery query, Pageable pageable) {
        String fromWhere = """
                 FROM room_member member
                 JOIN room_space room ON room.id = member.room_id AND room.deleted = FALSE
                """ + " WHERE " + roomChildWhere(query, "member");
        String dataSql = CONVERSATION_SCOPE_CTE + """
                SELECT member.id AS member_id, room.id AS room_id, room.name AS room_name,
                       member.user_id, member.display_name, member.member_type, member.status,
                       member.active_status, member.seat_no, member.joined_at, member.left_at,
                       member.last_active_at, member.created_at
                """ + fromWhere + " ORDER BY member.joined_at DESC, member.id DESC";
        return page(dataSql, CONVERSATION_SCOPE_CTE + "SELECT COUNT(*)" + fromWhere,
                query, pageable, (rs, rowNum) -> new ClocktowerAuditReportResponse.Member(
                        longValue(rs, "member_id"), longValue(rs, "room_id"), rs.getString("room_name"),
                        longValue(rs, "user_id"), rs.getString("display_name"), rs.getString("member_type"),
                        rs.getString("status"), booleanValue(rs, "active_status"), integerValue(rs, "seat_no"),
                        instant(rs, "joined_at"), instant(rs, "left_at"), instant(rs, "last_active_at"),
                        instant(rs, "created_at")));
    }

    public Page<ClocktowerAuditReportResponse.Invitation> invitations(ClocktowerAuditQuery query,
                                                                      Pageable pageable) {
        String fromWhere = """
                 FROM room_invitation invitation
                 JOIN room_space room ON room.id = invitation.room_id AND room.deleted = FALSE
                """ + " WHERE " + roomChildWhere(query, "invitation");
        String dataSql = CONVERSATION_SCOPE_CTE + """
                SELECT invitation.id AS invitation_id, room.id AS room_id, room.name AS room_name,
                       invitation.inviter_user_id, invitation.invitee_user_id, invitation.invitation_code,
                       invitation.status, invitation.active_status, invitation.target_seat_no,
                       invitation.expires_at, invitation.accepted_at, invitation.created_at
                """ + fromWhere + " ORDER BY invitation.created_at DESC, invitation.id DESC";
        return page(dataSql, CONVERSATION_SCOPE_CTE + "SELECT COUNT(*)" + fromWhere,
                query, pageable, (rs, rowNum) -> new ClocktowerAuditReportResponse.Invitation(
                        longValue(rs, "invitation_id"), longValue(rs, "room_id"), rs.getString("room_name"),
                        longValue(rs, "inviter_user_id"), longValue(rs, "invitee_user_id"),
                        rs.getString("invitation_code"), rs.getString("status"),
                        booleanValue(rs, "active_status"), integerValue(rs, "target_seat_no"),
                        instant(rs, "expires_at"), instant(rs, "accepted_at"), instant(rs, "created_at")));
    }

    public Page<ClocktowerAuditReportResponse.Ban> bans(ClocktowerAuditQuery query, Pageable pageable) {
        String fromWhere = """
                 FROM room_ban ban
                 JOIN room_space room ON room.id = ban.room_id AND room.deleted = FALSE
                """ + " WHERE " + roomChildWhere(query, "ban");
        String dataSql = CONVERSATION_SCOPE_CTE + """
                SELECT ban.id AS ban_id, room.id AS room_id, room.name AS room_name,
                       ban.user_id, ban.banned_by_user_id, ban.reason, ban.status,
                       ban.expires_at, ban.created_at
                """ + fromWhere + " ORDER BY ban.created_at DESC, ban.id DESC";
        return page(dataSql, CONVERSATION_SCOPE_CTE + "SELECT COUNT(*)" + fromWhere,
                query, pageable, (rs, rowNum) -> new ClocktowerAuditReportResponse.Ban(
                        longValue(rs, "ban_id"), longValue(rs, "room_id"), rs.getString("room_name"),
                        longValue(rs, "user_id"), longValue(rs, "banned_by_user_id"), rs.getString("reason"),
                        rs.getString("status"), instant(rs, "expires_at"), instant(rs, "created_at")));
    }

    private <T> Page<T> page(String dataSql, String countSql, ClocktowerAuditQuery query,
                             Pageable pageable, RowMapper<T> rowMapper) {
        MapSqlParameterSource parameters = parameters(query)
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());
        List<T> content = jdbcTemplate.query(dataSql + " LIMIT :limit OFFSET :offset", parameters, rowMapper);
        Long total = jdbcTemplate.queryForObject(countSql, parameters, Long.class);
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private String roomWhere(ClocktowerAuditQuery query) {
        StringBuilder where = new StringBuilder("room.deleted = FALSE");
        appendRoomFilters(where, query, "room");
        if (!query.gameIds().isEmpty()) {
            where.append(" AND EXISTS (SELECT 1 FROM clocktower_game selected_game")
                    .append(" WHERE selected_game.room_id = room.id AND selected_game.deleted = FALSE")
                    .append(" AND selected_game.id IN (:gameIds))");
        }
        if (!query.conversationIds().isEmpty()) {
            where.append(" AND EXISTS (SELECT 1 FROM clocktower_conversation_scope selected_scope")
                    .append(" WHERE selected_scope.room_id = room.id")
                    .append(" AND selected_scope.conversation_id IN (:conversationIds))");
        }
        return where.toString();
    }

    private String gameWhere(ClocktowerAuditQuery query) {
        StringBuilder where = new StringBuilder("game.deleted = FALSE");
        appendRoomFilters(where, query, "room");
        if (!query.gameIds().isEmpty()) {
            where.append(" AND game.id IN (:gameIds)");
        }
        if (!query.conversationIds().isEmpty()) {
            where.append(" AND EXISTS (SELECT 1 FROM clocktower_conversation_scope selected_scope")
                    .append(" WHERE selected_scope.game_id = game.id")
                    .append(" AND selected_scope.conversation_id IN (:conversationIds))");
        }
        return where.toString();
    }

    private String eventWhere(ClocktowerAuditQuery query) {
        return "event.deleted = FALSE AND " + gameWhere(query);
    }

    private String conversationWhere(ClocktowerAuditQuery query) {
        StringBuilder where = new StringBuilder("1 = 1");
        appendScopeFilters(where, query, "scope");
        return where.toString();
    }

    private String messageWhere(ClocktowerAuditQuery query) {
        StringBuilder where = new StringBuilder("message.deleted = FALSE AND message.status <> 'DELETED'");
        appendScopeFilters(where, query, "scope");
        return where.toString();
    }

    private String roomChildWhere(ClocktowerAuditQuery query, String childAlias) {
        StringBuilder where = new StringBuilder(childAlias).append(".deleted = FALSE");
        appendRoomFilters(where, query, "room");
        if (!query.gameIds().isEmpty()) {
            where.append(" AND EXISTS (SELECT 1 FROM clocktower_game selected_game")
                    .append(" WHERE selected_game.room_id = room.id AND selected_game.deleted = FALSE")
                    .append(" AND selected_game.id IN (:gameIds))");
        }
        if (!query.conversationIds().isEmpty()) {
            where.append(" AND EXISTS (SELECT 1 FROM clocktower_conversation_scope selected_scope")
                    .append(" WHERE selected_scope.room_id = room.id")
                    .append(" AND selected_scope.conversation_id IN (:conversationIds))");
        }
        return where.toString();
    }

    private void appendRoomFilters(StringBuilder where, ClocktowerAuditQuery query, String roomAlias) {
        where.append(" AND ").append(roomAlias).append(".context_type = 'CLOCKTOWER_ROOM'");
        if (!query.roomIds().isEmpty()) {
            where.append(" AND ").append(roomAlias).append(".id IN (:roomIds)");
        }
        if (query.roomName() != null) {
            where.append(" AND LOWER(").append(roomAlias).append(".name) LIKE :roomName ESCAPE '\\'");
        }
    }

    private void appendScopeFilters(StringBuilder where, ClocktowerAuditQuery query, String scopeAlias) {
        if (!query.roomIds().isEmpty()) {
            where.append(" AND ").append(scopeAlias).append(".room_id IN (:roomIds)");
        }
        if (!query.gameIds().isEmpty()) {
            where.append(" AND ").append(scopeAlias).append(".game_id IN (:gameIds)");
        }
        if (!query.conversationIds().isEmpty()) {
            where.append(" AND ").append(scopeAlias).append(".conversation_id IN (:conversationIds)");
        }
        if (query.roomName() != null) {
            where.append(" AND LOWER(").append(scopeAlias).append(".room_name) LIKE :roomName ESCAPE '\\'");
        }
    }

    private MapSqlParameterSource parameters(ClocktowerAuditQuery query) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (!query.roomIds().isEmpty()) {
            parameters.addValue("roomIds", query.roomIds());
        }
        if (!query.gameIds().isEmpty()) {
            parameters.addValue("gameIds", query.gameIds());
        }
        if (!query.conversationIds().isEmpty()) {
            parameters.addValue("conversationIds", query.conversationIds());
        }
        if (query.roomName() != null) {
            parameters.addValue("roomName", "%" + escapeLike(query.roomName().toLowerCase(Locale.ROOT)) + "%");
        }
        return parameters;
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Long longValue(ResultSet resultSet, String column) throws SQLException {
        Number value = (Number) resultSet.getObject(column);
        return value == null ? null : value.longValue();
    }

    private static Integer integerValue(ResultSet resultSet, String column) throws SQLException {
        Number value = (Number) resultSet.getObject(column);
        return value == null ? null : value.intValue();
    }

    private static Boolean booleanValue(ResultSet resultSet, String column) throws SQLException {
        return (Boolean) resultSet.getObject(column);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
