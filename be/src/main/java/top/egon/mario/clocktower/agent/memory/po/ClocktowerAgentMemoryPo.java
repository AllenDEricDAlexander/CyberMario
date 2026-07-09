package top.egon.mario.clocktower.agent.memory.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import top.egon.mario.common.entity.BaseAuditablePo;

@Getter
@Setter
@Entity
@Table(name = "clocktower_agent_memory")
public class ClocktowerAgentMemoryPo extends BaseAuditablePo {

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "agent_instance_id", nullable = false)
    private Long agentInstanceId;

    @Column(name = "game_seat_id", nullable = false)
    private Long gameSeatId;

    @Column(name = "source_event_id")
    private Long sourceEventId;

    @Column(name = "source_event_seq")
    private Long sourceEventSeq;

    @Column(name = "memory_type", nullable = false, length = 64)
    private String memoryType;

    @Column(name = "visibility", nullable = false, length = 32)
    private String visibility = "SELF";

    @Column(name = "subject_game_seat_id")
    private Long subjectGameSeatId;

    @Column(name = "subject_game_seat_id_key", nullable = false)
    private Long subjectGameSeatIdKey = -1L;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_json", nullable = false, columnDefinition = "jsonb")
    private String contentJson = "{}";

    @Column(name = "confidence", nullable = false)
    private int confidence = 50;

    @Column(name = "day_no", nullable = false)
    private int dayNo;

    @Column(name = "night_no", nullable = false)
    private int nightNo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";

    public void setSubjectGameSeatId(Long subjectGameSeatId) {
        this.subjectGameSeatId = subjectGameSeatId;
        this.subjectGameSeatIdKey = subjectGameSeatId == null ? -1L : subjectGameSeatId;
    }
}
