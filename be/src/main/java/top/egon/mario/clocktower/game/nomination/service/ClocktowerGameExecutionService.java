package top.egon.mario.clocktower.game.nomination.service;

import top.egon.mario.clocktower.game.nomination.dto.ClocktowerGameExecutionResolveRequest;
import top.egon.mario.clocktower.game.nomination.dto.ClocktowerGameExecutionResponse;
import top.egon.mario.clocktower.game.nomination.dto.ClocktowerGameNominationResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

public interface ClocktowerGameExecutionService {

    ClocktowerGameNominationResponse closeNomination(Long gameId, Long nominationId, RbacPrincipal principal);

    ClocktowerGameExecutionResponse resolveExecution(Long gameId,
                                                     ClocktowerGameExecutionResolveRequest request,
                                                     RbacPrincipal principal);
}
