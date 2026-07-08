package top.egon.mario.clocktower.game.action.service;

import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionRequest;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

public interface ClocktowerHumanGameActionService {

    ClocktowerGameActionResponse submit(Long gameId, ClocktowerGameActionRequest request, RbacPrincipal principal);
}
