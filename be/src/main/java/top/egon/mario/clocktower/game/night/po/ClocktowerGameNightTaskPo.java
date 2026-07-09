package top.egon.mario.clocktower.game.night.po;

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
@Table(name = "clocktower_game_night_task")
public class ClocktowerGameNightTaskPo extends BaseAuditablePo {

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "night_no", nullable = false)
    private int nightNo;

    @Column(name = "task_key", nullable = false, length = 128)
    private String taskKey;

    @Column(name = "actor_game_seat_id")
    private Long actorGameSeatId;

    @Column(name = "role_code", length = 64)
    private String roleCode;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "PENDING";

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "task_type", nullable = false, length = 64)
    private String taskType = "ST_RESOLVE";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "choice_json", nullable = false, columnDefinition = "jsonb")
    private String choiceJson = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", nullable = false, columnDefinition = "jsonb")
    private String resultJson = "{}";

    @Column(name = "resolved_by_actor_id")
    private Long resolvedByActorId;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "skipped_at")
    private Instant skippedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
