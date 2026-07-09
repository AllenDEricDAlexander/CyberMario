package top.egon.mario.clocktower.game.night.service.impl;

import org.springframework.stereotype.Service;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.game.night.dto.ClocktowerNightResolveRequest;
import top.egon.mario.clocktower.game.night.dto.ClocktowerNightTaskView;
import top.egon.mario.clocktower.game.night.service.ClocktowerNightResolutionService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

@Service
public class ClocktowerNightResolutionServiceImpl implements ClocktowerNightResolutionService {

    @Override
    public ClocktowerNightTaskView resolveTask(Long gameId, Long taskId, ClocktowerNightResolveRequest request,
                                               RbacPrincipal principal) {
        throw new ClocktowerException("CLOCKTOWER_NIGHT_RESOLUTION_SERVICE_NOT_READY");
    }

    @Override
    public List<ClocktowerNightTaskView> resolveReady(Long gameId, RbacPrincipal principal) {
        return List.of();
    }
}
