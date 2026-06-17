package top.egon.mario.clocktower.board.dto.request;

import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;

import java.util.List;

public record ClocktowerBoardValidateRequest(
        ClocktowerScriptCode scriptCode,
        int playerCount,
        List<String> roleCodes
) {
}
