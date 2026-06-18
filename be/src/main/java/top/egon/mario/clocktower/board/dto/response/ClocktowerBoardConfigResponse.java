package top.egon.mario.clocktower.board.dto.response;

import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.script.dto.response.ClocktowerRoleSummaryResponse;

import java.util.List;

public record ClocktowerBoardConfigResponse(
        Long boardId,
        String boardCode,
        ClocktowerScriptCode scriptCode,
        int playerCount,
        List<String> roleCodes,
        List<ClocktowerRoleSummaryResponse> roles,
        ClocktowerBoardValidationResponse validation
) {
}
