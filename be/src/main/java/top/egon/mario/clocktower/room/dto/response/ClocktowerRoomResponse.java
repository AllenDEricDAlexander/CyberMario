package top.egon.mario.clocktower.room.dto.response;

import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerRoomStatus;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.room.po.ClocktowerRoomPo;

import java.util.List;

public record ClocktowerRoomResponse(
        Long roomId,
        String roomCode,
        String name,
        ClocktowerScriptCode scriptCode,
        ClocktowerRoomStatus status,
        ClocktowerPhase phase,
        int playerCount,
        Long storytellerUserId,
        List<ClocktowerSeatResponse> seats
) {

    public static ClocktowerRoomResponse from(ClocktowerRoomPo room, List<ClocktowerSeatResponse> seats) {
        return new ClocktowerRoomResponse(room.getId(), room.getRoomCode(), room.getName(), room.getScriptCode(),
                room.getStatus(), room.getPhase(), room.getPlayerCount(), room.getStorytellerUserId(), seats);
    }
}
