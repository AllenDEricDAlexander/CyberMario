package top.egon.mario.clocktower.script.dto.response;

import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.script.po.ClocktowerScriptPo;

public record ClocktowerScriptResponse(
        ClocktowerScriptCode scriptCode,
        String name,
        String edition,
        int minPlayers,
        int maxPlayers,
        int roleCount,
        boolean enabled,
        String sourceUrl
) {

    public static ClocktowerScriptResponse from(ClocktowerScriptPo script) {
        return new ClocktowerScriptResponse(script.getScriptCode(), script.getName(), script.getEdition(),
                script.getMinPlayers(), script.getMaxPlayers(), script.getRoleCount(), script.isEnabled(),
                script.getSourceUrl());
    }
}
