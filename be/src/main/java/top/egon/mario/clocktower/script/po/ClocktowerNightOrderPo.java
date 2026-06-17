package top.egon.mario.clocktower.script.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.common.entity.BaseAuditablePo;

@Getter
@Setter
@Entity
@Table(name = "clocktower_night_order")
public class ClocktowerNightOrderPo extends BaseAuditablePo {

    @Enumerated(EnumType.STRING)
    @Column(name = "script_code", nullable = false, length = 64)
    private ClocktowerScriptCode scriptCode;

    @Column(name = "role_code", nullable = false, length = 64)
    private String roleCode;

    @Column(name = "night_type", nullable = false, length = 32)
    private String nightType;

    @Column(name = "order_no", nullable = false)
    private int orderNo;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "wake_condition")
    private String wakeCondition;

    @Column(name = "reminder_text")
    private String reminderText;
}
