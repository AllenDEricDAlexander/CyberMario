package top.egon.mario.clocktower.game.night.service;

import top.egon.mario.clocktower.game.night.dto.ClocktowerNightResolveRequest;
import top.egon.mario.clocktower.game.night.dto.ClocktowerNightTaskView;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

public interface ClocktowerNightResolutionService {

    ClocktowerNightTaskView resolveTask(Long gameId, Long taskId, ClocktowerNightResolveRequest request,
                                        RbacPrincipal principal);

    List<ClocktowerNightTaskView> resolveReady(Long gameId, RbacPrincipal principal);
}
