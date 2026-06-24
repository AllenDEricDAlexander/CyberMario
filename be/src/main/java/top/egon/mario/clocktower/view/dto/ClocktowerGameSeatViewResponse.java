package top.egon.mario.clocktower.view.dto;

import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;

public record ClocktowerGameSeatViewResponse(
        Long gameSeatId,
        Long roomSeatId,
        int seatNo,
        Long userId,
        String displayName,
        String roleCode,
        String roleType,
        String alignment,
        String lifeStatus,
        String publicLifeStatus,
        boolean hasDeadVote,
        boolean traveler,
        String status
) {

    public static ClocktowerGameSeatViewResponse publicView(ClocktowerGameSeatPo seat) {
        return new ClocktowerGameSeatViewResponse(seat.getId(), seat.getRoomSeatId(), seat.getSeatNo(),
                seat.getUserId(), seat.getDisplayName(), null, null, null, seat.getPublicLifeStatus(),
                seat.getPublicLifeStatus(), seat.isHasDeadVote(), seat.isTraveler(), seat.getStatus());
    }

    public static ClocktowerGameSeatViewResponse fullView(ClocktowerGameSeatPo seat) {
        return new ClocktowerGameSeatViewResponse(seat.getId(), seat.getRoomSeatId(), seat.getSeatNo(),
                seat.getUserId(), seat.getDisplayName(), seat.getRoleCode(), seat.getRoleType(),
                seat.getAlignment(), seat.getLifeStatus(), seat.getPublicLifeStatus(), seat.isHasDeadVote(),
                seat.isTraveler(), seat.getStatus());
    }
}
