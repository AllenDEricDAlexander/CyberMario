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
@Table(name = "clocktower_game")
public class ClocktowerGamePo extends BaseAuditablePo {

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "game_no", nullable = false)
    private int gameNo;

    @Column(name = "script_code", nullable = false, length = 64)
    private String scriptCode;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "SETUP";

    @Column(name = "phase", nullable = false, length = 32)
    private String phase = "LOBBY";

    @Column(name = "day_no", nullable = false)
    private int dayNo;

    @Column(name = "night_no", nullable = false)
    private int nightNo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "board_snapshot_json", nullable = false, columnDefinition = "jsonb")
    private String boardSnapshotJson = "{}";

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
