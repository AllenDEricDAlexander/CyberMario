package top.egon.mario.clocktower.board.dto.request;

import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;

public record ClocktowerBoardQuery(
        ClocktowerScriptCode scriptCode,
        Integer playerCount,
        Boolean valid
) {
}
