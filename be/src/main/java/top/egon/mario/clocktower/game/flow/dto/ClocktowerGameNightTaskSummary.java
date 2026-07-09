package top.egon.mario.clocktower.game.flow.dto;

public record ClocktowerGameNightTaskSummary(
        int mandatoryCount,
        int pendingMandatoryCount,
        int doneCount,
        int skippedCount
) {

    public boolean complete() {
        return pendingMandatoryCount == 0;
    }
}
