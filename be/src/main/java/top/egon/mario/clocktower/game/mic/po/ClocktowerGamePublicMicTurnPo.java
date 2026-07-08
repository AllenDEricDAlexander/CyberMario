package top.egon.mario.clocktower.game.mic.po;

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
@Table(name = "clocktower_game_public_mic_turn")
public class ClocktowerGamePublicMicTurnPo extends BaseAuditablePo {

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "day_no", nullable = false)
    private int dayNo;

    @Column(name = "game_seat_id", nullable = false)
    private Long gameSeatId;

    @Column(name = "turn_order", nullable = false)
    private int turnOrder;

    @Column(name = "stage", nullable = false, length = 32)
    private String stage;

    @Column(name = "acquisition_type", nullable = false, length = 32)
    private String acquisitionType;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "speech_event_id")
    private Long speechEventId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
