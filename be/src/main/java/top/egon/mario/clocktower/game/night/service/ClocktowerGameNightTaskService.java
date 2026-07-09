package top.egon.mario.clocktower.game.night.service;

import top.egon.mario.clocktower.game.action.dto.ActorContext;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionResponse;
import top.egon.mario.clocktower.game.action.dto.GameActionCommand;
import top.egon.mario.clocktower.game.night.dto.ClocktowerNightSkipRequest;
import top.egon.mario.clocktower.game.night.dto.ClocktowerNightTaskView;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

public interface ClocktowerGameNightTaskService {

    void initializeNightTasks(ClocktowerGamePo game);

    List<ClocktowerNightTaskView> currentTasks(Long gameId, RbacPrincipal principal);

    ClocktowerGameActionResponse submitChoice(ClocktowerGamePo game, GameActionCommand command, ActorContext actor);

    ClocktowerGameActionResponse autoChooseTask(Long gameId, Long taskId, Long agentInstanceId);

    ClocktowerNightTaskView skipTask(Long gameId, Long taskId, ClocktowerNightSkipRequest request,
                                     RbacPrincipal principal);
}
