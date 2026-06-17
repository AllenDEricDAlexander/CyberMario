package top.egon.mario.clocktower.board.dto.request;

import top.egon.mario.clocktower.board.dto.response.ClocktowerBoardValidationResponse;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;

import java.util.List;

public record ClocktowerBoardSaveRequest(
        ClocktowerScriptCode scriptCode,
        int playerCount,
        int difficulty,
        int chaos,
        int evilPressure,
        boolean newbieFriendly,
        String seed,
        List<String> roleCodes,
        ClocktowerBoardValidationResponse validation
) {
}
