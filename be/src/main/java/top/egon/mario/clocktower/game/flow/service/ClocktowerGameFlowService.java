package top.egon.mario.clocktower.game.flow.service;

import top.egon.mario.clocktower.game.flow.dto.ClocktowerGameAdvanceRequest;
import top.egon.mario.clocktower.game.flow.dto.ClocktowerGameAdvanceResult;
import top.egon.mario.clocktower.game.flow.dto.ClocktowerGameFlowView;
import top.egon.mario.rbac.service.security.RbacPrincipal;

public interface ClocktowerGameFlowService {

    ClocktowerGameFlowView getFlow(Long gameId, RbacPrincipal principal);

    ClocktowerGameAdvanceResult advance(Long gameId, ClocktowerGameAdvanceRequest request,
                                        RbacPrincipal principal);

    ClocktowerGameAdvanceResult forceAdvance(Long gameId, ClocktowerGameAdvanceRequest request,
                                             RbacPrincipal principal);
}
