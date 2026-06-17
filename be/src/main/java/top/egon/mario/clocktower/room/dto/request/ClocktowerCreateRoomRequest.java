package top.egon.mario.clocktower.room.dto.request;

import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;

import java.util.List;

public record ClocktowerCreateRoomRequest(
        String name,
        ClocktowerScriptCode scriptCode,
        int playerCount,
        Long boardConfigId,
        List<String> roleCodes,
        String storytellerMode,
        boolean allowSpectators,
        boolean allowPrivateChat
) {

    public ClocktowerRoomCreateRequest toCreateRequest() {
        return new ClocktowerRoomCreateRequest(name, scriptCode, playerCount, boardConfigId, null, roleCodes,
                storytellerMode, allowSpectators, allowPrivateChat, 0);
    }
}
