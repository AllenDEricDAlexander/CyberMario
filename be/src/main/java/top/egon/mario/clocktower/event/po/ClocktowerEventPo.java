package top.egon.mario.clocktower.event.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import top.egon.mario.clocktower.common.enums.ClocktowerEventType;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerVisibility;
import top.egon.mario.common.entity.BaseAuditablePo;

@Getter
@Setter
@Entity
@Table(name = "clocktower_event")
public class ClocktowerEventPo extends BaseAuditablePo {

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "event_seq", nullable = false)
    private Long eventSeq;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private ClocktowerEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "phase", nullable = false, length = 32)
    private ClocktowerPhase phase;

    @Column(name = "day_no", nullable = false)
    private int dayNo;

    @Column(name = "night_no", nullable = false)
    private int nightNo;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_seat_id")
    private Long actorSeatId;

    @Column(name = "target_seat_id")
    private Long targetSeatId;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 32)
    private ClocktowerVisibility visibility;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "visible_seat_ids_json", nullable = false, columnDefinition = "jsonb")
    private String visibleSeatIdsJson = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payloadJson = "{}";
}
