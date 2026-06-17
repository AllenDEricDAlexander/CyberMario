package top.egon.mario.clocktower.board.dto.response;

import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;

import java.util.List;

public record ClocktowerBoardCandidateResponse(
        String candidateId,
        ClocktowerScriptCode scriptCode,
        int playerCount,
        List<String> roleCodes,
        ClocktowerBoardValidationResponse validation,
        List<ClocktowerScoreResponse> scores
) {
}
