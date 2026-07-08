package top.egon.mario.clocktower.game.nomination.po;

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
@Table(name = "clocktower_game_execution")
public class ClocktowerGameExecutionPo extends BaseAuditablePo {

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "day_no", nullable = false)
    private int dayNo;

    @Column(name = "nominee_game_seat_id")
    private Long nomineeGameSeatId;

    @Column(name = "nomination_id")
    private Long nominationId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "executed", nullable = false)
    private boolean executed;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
