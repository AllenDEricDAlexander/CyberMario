package top.egon.mario.clocktower.board.dto.response;

import java.util.List;

public record BoardValidationResponse(
        boolean valid,
        ClocktowerRoleTypeCountResponse typeCounts,
        List<ClocktowerRuleViolationResponse> issues,
        List<ClocktowerScoreResponse> scores
) {
}
