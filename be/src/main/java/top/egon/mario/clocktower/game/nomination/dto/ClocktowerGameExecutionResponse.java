package top.egon.mario.clocktower.game.nomination.dto;

import top.egon.mario.clocktower.game.nomination.po.ClocktowerGameExecutionPo;

public record ClocktowerGameExecutionResponse(
        Long executionId,
        Long gameId,
        int dayNo,
        Long nomineeGameSeatId,
        Long nominationId,
        String status,
        boolean executed
) {

    public static ClocktowerGameExecutionResponse from(ClocktowerGameExecutionPo execution) {
        if (execution == null) {
            return null;
        }
        return new ClocktowerGameExecutionResponse(
                execution.getId(),
                execution.getGameId(),
                execution.getDayNo(),
                execution.getNomineeGameSeatId(),
                execution.getNominationId(),
                execution.getStatus(),
                execution.isExecuted()
        );
    }
}
