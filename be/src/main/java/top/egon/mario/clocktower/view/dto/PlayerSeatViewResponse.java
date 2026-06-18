package top.egon.mario.clocktower.view.dto;

import top.egon.mario.clocktower.common.enums.ClocktowerAlignment;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.room.po.ClocktowerSeatPo;

public record PlayerSeatViewResponse(
        Long seatId,
        int seatNo,
        String displayName,
        String roleCode,
        ClocktowerRoleType roleType,
        ClocktowerAlignment alignment,
        String lifeStatus,
        String publicLifeStatus,
        boolean hasDeadVote
) {

    public static PlayerSeatViewResponse from(ClocktowerSeatPo seat) {
        return new PlayerSeatViewResponse(seat.getId(), seat.getSeatNo(), seat.getDisplayName(), seat.getRoleCode(),
                seat.getRoleType(), seat.getAlignment(), seat.getLifeStatus(), seat.getPublicLifeStatus(),
                seat.isHasDeadVote());
    }
}
