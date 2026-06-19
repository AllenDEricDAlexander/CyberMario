package top.egon.mario.clocktower.flow.dto;

import top.egon.mario.clocktower.grimoire.dto.response.GamePhaseResponse;

import java.util.List;

public record ClocktowerFlowResponse(
        Long roomId,
        GamePhaseResponse phase,
        ClocktowerFlowTransition nextTransition,
        boolean advanceAllowed,
        List<String> blockingReasons,
        NightTaskSummaryResponse nightTaskSummary,
        NominationSummaryResponse openNomination,
        ExecutionCandidateResponse executionCandidate,
        VictoryCandidateResponse victoryCandidate
) {
}
