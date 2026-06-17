package top.egon.mario.clocktower.room.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.common.entity.BaseAuditablePo;

@Getter
@Setter
@Entity
@Table(name = "clocktower_seat")
public class ClocktowerSeatPo extends BaseAuditablePo {

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "seat_no", nullable = false)
    private int seatNo;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "role_code", length = 64)
    private String roleCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_type", length = 32)
    private ClocktowerRoleType roleType;

    @Column(name = "alignment", length = 32)
    private String alignment;

    @Column(name = "life_status", nullable = false, length = 32)
    private String lifeStatus = "ALIVE";

    @Column(name = "connected", nullable = false)
    private boolean connected;

    @Column(name = "has_dead_vote", nullable = false)
    private boolean hasDeadVote = true;

    @Column(name = "is_traveler", nullable = false)
    private boolean traveler;
}
