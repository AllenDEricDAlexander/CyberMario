package top.egon.mario.clocktower.replay.dto;

import top.egon.mario.clocktower.game.po.ClocktowerGamePo;

import java.time.Instant;

public record ClocktowerGameHistoryResponse(
        Long gameId,
        Long roomId,
        int gameNo,
        String scriptCode,
        String status,
        String phase,
        Instant startedAt,
        Instant endedAt
) {

    public static ClocktowerGameHistoryResponse from(ClocktowerGamePo game) {
        return new ClocktowerGameHistoryResponse(game.getId(), game.getRoomId(), game.getGameNo(),
                game.getScriptCode(), game.getStatus(), game.getPhase(), game.getStartedAt(), game.getEndedAt());
    }
}
