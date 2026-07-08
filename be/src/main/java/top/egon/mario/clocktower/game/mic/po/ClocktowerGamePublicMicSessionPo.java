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
@Table(name = "clocktower_game_public_mic_session")
public class ClocktowerGamePublicMicSessionPo extends BaseAuditablePo {

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "day_no", nullable = false)
    private int dayNo;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "current_holder_game_seat_id")
    private Long currentHolderGameSeatId;

    @Column(name = "current_turn_id")
    private Long currentTurnId;

    @Column(name = "round_started_at")
    private Instant roundStartedAt;

    @Column(name = "round_finished_at")
    private Instant roundFinishedAt;

    @Column(name = "grab_started_at")
    private Instant grabStartedAt;

    @Column(name = "grab_ends_at")
    private Instant grabEndsAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
