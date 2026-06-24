package top.egon.mario.clocktower.room.dto.request;

import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;

import java.util.List;

public record ClocktowerRoomBoardSwitchRequest(
        ClocktowerScriptCode scriptCode,
        int playerCount,
        Long boardConfigId,
        String boardCode,
        List<String> roleCodes
) {
}
