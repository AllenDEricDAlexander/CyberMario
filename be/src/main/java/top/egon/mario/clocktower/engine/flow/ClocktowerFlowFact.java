package top.egon.mario.clocktower.engine.flow;

import top.egon.mario.clocktower.common.enums.ClocktowerPhase;

public record ClocktowerFlowFact(
        ClocktowerPhase phase,
        int dayNo,
        int nightNo,
        int pendingNightTaskCount,
        boolean openNominationExists,
        boolean executionResolved,
        boolean demonAlive,
        boolean allDemonsDead,
        int realAliveCount,
        int executionTopVoteCount,
        boolean executionTopVoteTied,
        boolean executionCandidateExists
) {
}
