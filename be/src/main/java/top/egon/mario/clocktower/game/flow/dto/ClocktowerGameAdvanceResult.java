package top.egon.mario.clocktower.game.flow.dto;

public record ClocktowerGameAdvanceResult(
        Long gameId,
        String previousPhase,
        String phase,
        boolean advanced,
        boolean forced,
        ClocktowerGameFlowView flow
) {
}
