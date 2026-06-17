package top.egon.mario.clocktower.replay.dto;

import top.egon.mario.clocktower.grimoire.po.ClocktowerVotePo;

public record ClocktowerVoteReplayResponse(
        Long voteId,
        Long nominationId,
        Long voterSeatId,
        boolean voteValue,
        boolean usedDeadVote,
        Long eventId
) {

    public static ClocktowerVoteReplayResponse from(ClocktowerVotePo vote) {
        return new ClocktowerVoteReplayResponse(vote.getId(), vote.getNominationId(), vote.getVoterSeatId(),
                vote.isVoteValue(), vote.isUsedDeadVote(), vote.getEventId());
    }
}
