package top.egon.mario.clocktower.grimoire.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.common.entity.BaseAuditablePo;

@Getter
@Setter
@Entity
@Table(name = "clocktower_storyteller_task")
public class ClocktowerStorytellerTaskPo extends BaseAuditablePo {

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "task_type", nullable = false, length = 64)
    private String taskType;

    @Enumerated(EnumType.STRING)
    @Column(name = "phase", nullable = false, length = 32)
    private ClocktowerPhase phase;

    @Column(name = "day_no", nullable = false)
    private int dayNo;

    @Column(name = "night_no", nullable = false)
    private int nightNo;

    @Column(name = "role_code", length = 64)
    private String roleCode;

    @Column(name = "seat_id")
    private Long seatId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "note")
    private String note;
}
