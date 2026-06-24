package top.egon.mario.clocktower.room.dto.request;

import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;

import java.util.List;

public record ClocktowerRoomCreateRequest(
        String name,
        ClocktowerScriptCode scriptCode,
        int playerCount,
        Long boardConfigId,
        String boardCode,
        List<String> roleCodes,
        String storytellerMode,
        boolean allowSpectators,
        boolean allowPrivateChat,
        int agentSeatCount,
        String visibility,
        String seatingPolicy
) {

    public ClocktowerRoomCreateRequest(String name, ClocktowerScriptCode scriptCode, int playerCount,
                                       Long boardConfigId, String boardCode, List<String> roleCodes,
                                       String storytellerMode, boolean allowSpectators, boolean allowPrivateChat,
                                       int agentSeatCount) {
        this(name, scriptCode, playerCount, boardConfigId, boardCode, roleCodes, storytellerMode,
                allowSpectators, allowPrivateChat, agentSeatCount, null, null);
    }
}
