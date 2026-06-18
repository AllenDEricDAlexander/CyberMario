package top.egon.mario.clocktower.board.dto.response;

import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.script.dto.response.ClocktowerRoleSummaryResponse;

import java.util.List;

public record ClocktowerBoardCandidateResponse(
        String candidateId,
        ClocktowerScriptCode scriptCode,
        int playerCount,
        List<String> roleCodes,
        List<ClocktowerRoleSummaryResponse> roles,
        ClocktowerBoardValidationResponse validation,
        List<ClocktowerScoreResponse> scores
) {
}
