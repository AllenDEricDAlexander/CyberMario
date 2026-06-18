package top.egon.mario.clocktower.board.dto.response;

import java.util.List;
import java.util.Map;

public record ClocktowerBoardValidationResponse(
        boolean valid,
        Map<String, Integer> roleTypeCounts,
        List<ClocktowerRuleViolationResponse> violations,
        List<ClocktowerScoreResponse> scores
) {

    public static ClocktowerBoardValidationResponse from(BoardValidationResponse response,
                                                         Map<String, Integer> roleTypeCounts) {
        return new ClocktowerBoardValidationResponse(response.valid(), roleTypeCounts, response.issues(), response.scores());
    }
}
