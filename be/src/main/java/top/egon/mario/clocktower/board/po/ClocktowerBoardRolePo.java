package top.egon.mario.clocktower.board.po;

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
@Table(name = "clocktower_board_role")
public class ClocktowerBoardRolePo extends BaseAuditablePo {

    @Column(name = "board_config_id", nullable = false)
    private Long boardConfigId;

    @Column(name = "role_code", nullable = false, length = 64)
    private String roleCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_type", nullable = false, length = 32)
    private ClocktowerRoleType roleType;

    @Column(name = "seat_no")
    private Integer seatNo;

    @Column(name = "locked", nullable = false)
    private boolean locked;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
