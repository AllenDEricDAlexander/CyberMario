package top.egon.mario.clocktower.board.dto.response;

import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;

import java.util.List;
import java.util.Map;

public record ClocktowerBoardValidationResponse(
        boolean valid,
        Map<ClocktowerRoleType, Integer> roleTypeCounts,
        List<ClocktowerRuleViolationResponse> violations,
        List<ClocktowerScoreResponse> scores
) {

    public static ClocktowerBoardValidationResponse from(BoardValidationResponse response,
                                                         Map<ClocktowerRoleType, Integer> roleTypeCounts) {
        return new ClocktowerBoardValidationResponse(response.valid(), roleTypeCounts, response.issues(), response.scores());
    }
}
