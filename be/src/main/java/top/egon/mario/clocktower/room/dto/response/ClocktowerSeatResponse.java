package top.egon.mario.clocktower.room.dto.response;

import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.room.po.ClocktowerSeatPo;

public record ClocktowerSeatResponse(
        Long seatId,
        int seatNo,
        Long userId,
        String displayName,
        String roleCode,
        ClocktowerRoleType roleType,
        String lifeStatus,
        String publicLifeStatus,
        boolean connected,
        boolean hasDeadVote
) {

    public static ClocktowerSeatResponse from(ClocktowerSeatPo seat) {
        return new ClocktowerSeatResponse(seat.getId(), seat.getSeatNo(), seat.getUserId(), seat.getDisplayName(),
                seat.getRoleCode(), seat.getRoleType(), seat.getLifeStatus(), seat.getPublicLifeStatus(),
                seat.isConnected(), seat.isHasDeadVote());
    }

    public static ClocktowerSeatResponse publicView(ClocktowerSeatPo seat) {
        return new ClocktowerSeatResponse(seat.getId(), seat.getSeatNo(), seat.getUserId(), seat.getDisplayName(),
                null, null, seat.getPublicLifeStatus(), seat.getPublicLifeStatus(), seat.isConnected(),
                seat.isHasDeadVote());
    }
}
