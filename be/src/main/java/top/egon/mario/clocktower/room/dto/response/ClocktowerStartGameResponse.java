package top.egon.mario.clocktower.room.dto.response;

import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerRoomStatus;

public record ClocktowerStartGameResponse(
        Long roomId,
        ClocktowerRoomStatus status,
        ClocktowerPhase phase
) {
}
