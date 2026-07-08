package top.egon.mario.clocktower.game.nomination.dto;

import top.egon.mario.clocktower.game.nomination.po.ClocktowerGameNominationPo;

public record ClocktowerGameNominationResponse(
        Long nominationId,
        Long gameId,
        int dayNo,
        Long nominatorGameSeatId,
        Long nomineeGameSeatId,
        String status,
        int voteCount,
        int requiredVotes,
        ClocktowerGameExecutionResponse execution
) {

    public static ClocktowerGameNominationResponse from(ClocktowerGameNominationPo nomination,
                                                        ClocktowerGameExecutionResponse execution) {
        return new ClocktowerGameNominationResponse(
                nomination.getId(),
                nomination.getGameId(),
                nomination.getDayNo(),
                nomination.getNominatorGameSeatId(),
                nomination.getNomineeGameSeatId(),
                nomination.getStatus(),
                nomination.getVoteCount(),
                nomination.getRequiredVotes(),
                execution
        );
    }
}
