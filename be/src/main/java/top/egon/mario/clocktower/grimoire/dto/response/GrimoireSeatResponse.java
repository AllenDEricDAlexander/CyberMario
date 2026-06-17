package top.egon.mario.clocktower.grimoire.dto.response;

import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.grimoire.po.ClocktowerGrimoireEntryPo;
import top.egon.mario.clocktower.room.po.ClocktowerSeatPo;

public record GrimoireSeatResponse(
        Long seatId,
        int seatNo,
        Long userId,
        String displayName,
        String roleCode,
        ClocktowerRoleType roleType,
        String alignment,
        boolean alive,
        boolean hasDeadVote,
        boolean connected,
        String notes
) {

    public static GrimoireSeatResponse from(ClocktowerSeatPo seat, ClocktowerGrimoireEntryPo entry) {
        return new GrimoireSeatResponse(seat.getId(), seat.getSeatNo(), seat.getUserId(), seat.getDisplayName(),
                entry == null ? seat.getRoleCode() : entry.getRoleCode(),
                entry == null ? seat.getRoleType() : entry.getRoleType(),
                entry == null ? seat.getAlignment() : entry.getAlignment(),
                "ALIVE".equals(seat.getLifeStatus()), seat.isHasDeadVote(), seat.isConnected(),
                entry == null ? null : entry.getNotes());
    }
}
