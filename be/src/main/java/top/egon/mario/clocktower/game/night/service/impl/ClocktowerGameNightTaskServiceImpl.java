package top.egon.mario.clocktower.game.night.service.impl;

import org.springframework.stereotype.Service;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.game.action.dto.ActorContext;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionResponse;
import top.egon.mario.clocktower.game.action.dto.GameActionCommand;
import top.egon.mario.clocktower.game.night.dto.ClocktowerNightSkipRequest;
import top.egon.mario.clocktower.game.night.dto.ClocktowerNightTaskView;
import top.egon.mario.clocktower.game.night.service.ClocktowerGameNightTaskService;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

@Service
public class ClocktowerGameNightTaskServiceImpl implements ClocktowerGameNightTaskService {

    @Override
    public void initializeNightTasks(ClocktowerGamePo game) {
    }

    @Override
    public List<ClocktowerNightTaskView> currentTasks(Long gameId, RbacPrincipal principal) {
        return List.of();
    }

    @Override
    public ClocktowerGameActionResponse submitChoice(ClocktowerGamePo game, GameActionCommand command,
                                                    ActorContext actor) {
        return ClocktowerGameActionResponse.rejected("CLOCKTOWER_NIGHT_TASK_SERVICE_NOT_READY");
    }

    @Override
    public ClocktowerGameActionResponse autoChooseTask(Long gameId, Long taskId, Long agentInstanceId) {
        return ClocktowerGameActionResponse.rejected("CLOCKTOWER_NIGHT_TASK_SERVICE_NOT_READY");
    }

    @Override
    public ClocktowerNightTaskView skipTask(Long gameId, Long taskId, ClocktowerNightSkipRequest request,
                                           RbacPrincipal principal) {
        throw new ClocktowerException("CLOCKTOWER_NIGHT_TASK_SERVICE_NOT_READY");
    }
}
