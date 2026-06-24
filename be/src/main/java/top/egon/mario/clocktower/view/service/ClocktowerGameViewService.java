package top.egon.mario.clocktower.view.service;

import top.egon.mario.clocktower.view.dto.ClocktowerGameViewResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

public interface ClocktowerGameViewService {

    ClocktowerGameViewResponse gameView(Long gameId, RbacPrincipal principal);
}
