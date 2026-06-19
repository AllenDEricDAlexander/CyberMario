package top.egon.mario.clocktower.flow.dto;

public record NightTaskSummaryResponse(
        int total,
        int pending,
        int done,
        int skipped
) {
}
