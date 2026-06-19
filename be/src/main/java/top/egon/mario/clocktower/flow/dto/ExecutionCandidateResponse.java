package top.egon.mario.clocktower.flow.dto;

public record ExecutionCandidateResponse(
        boolean resolved,
        boolean executable,
        Long nominationId,
        Long nomineeSeatId,
        int voteCount,
        int threshold,
        String reason
) {
}
