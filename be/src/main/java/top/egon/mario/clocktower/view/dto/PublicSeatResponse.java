package top.egon.mario.clocktower.view.dto;

import top.egon.mario.clocktower.room.po.ClocktowerSeatPo;

public record PublicSeatResponse(
        Long seatId,
        int seatNo,
        String displayName,
        String roleCode,
        String lifeStatus,
        boolean connected,
        boolean hasDeadVote
) {

    public static PublicSeatResponse from(ClocktowerSeatPo seat) {
        return new PublicSeatResponse(seat.getId(), seat.getSeatNo(), seat.getDisplayName(), null,
                seat.getLifeStatus(), seat.isConnected(), seat.isHasDeadVote());
    }
}
