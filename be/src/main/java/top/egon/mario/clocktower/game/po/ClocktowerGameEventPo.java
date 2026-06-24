package top.egon.mario.clocktower.game.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import top.egon.mario.common.entity.BaseAuditablePo;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "clocktower_game_event")
public class ClocktowerGameEventPo extends BaseAuditablePo {

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "event_seq", nullable = false)
    private Long eventSeq;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "phase", nullable = false, length = 32)
    private String phase;

    @Column(name = "day_no", nullable = false)
    private int dayNo;

    @Column(name = "night_no", nullable = false)
    private int nightNo;

    @Column(name = "actor_game_seat_id")
    private Long actorGameSeatId;

    @Column(name = "target_game_seat_id")
    private Long targetGameSeatId;

    @Column(name = "visibility", nullable = false, length = 32)
    private String visibility;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "visible_game_seat_ids_json", nullable = false, columnDefinition = "jsonb")
    private String visibleGameSeatIdsJson = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payloadJson = "{}";

    @Column(name = "status", nullable = false, length = 32)
    private String status = "VISIBLE";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
