package top.egon.mario.clocktower.game.nomination.dto;

public record ClocktowerGameExecutionResolveRequest(
        Boolean execute,
        Long targetGameSeatId,
        Long nominationId,
        String note
) {
}
