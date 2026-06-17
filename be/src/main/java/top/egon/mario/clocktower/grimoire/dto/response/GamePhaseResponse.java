package top.egon.mario.clocktower.grimoire.dto.response;

import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.room.po.ClocktowerRoomPo;

public record GamePhaseResponse(
        ClocktowerPhase phase,
        int dayNo,
        int nightNo
) {

    public static GamePhaseResponse from(ClocktowerRoomPo room) {
        return new GamePhaseResponse(room.getPhase(), room.getCurrentDayNo(), room.getCurrentNightNo());
    }
}
