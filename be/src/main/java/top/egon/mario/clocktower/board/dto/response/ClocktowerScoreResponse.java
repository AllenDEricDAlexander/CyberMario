package top.egon.mario.clocktower.board.dto.response;

import top.egon.mario.clocktower.engine.ScoreDecision;

public record ClocktowerScoreResponse(String scoreType, int delta, String reason) {

    public static ClocktowerScoreResponse from(ScoreDecision decision) {
        return new ClocktowerScoreResponse(decision.scoreType(), decision.delta(), decision.reason());
    }
}
