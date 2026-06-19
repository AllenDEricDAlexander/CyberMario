package top.egon.mario.clocktower.flow.dto;

public record NominationSummaryResponse(
        Long nominationId,
        Long nominatorSeatId,
        Long nomineeSeatId,
        int voteCount,
        String status
) {
}
