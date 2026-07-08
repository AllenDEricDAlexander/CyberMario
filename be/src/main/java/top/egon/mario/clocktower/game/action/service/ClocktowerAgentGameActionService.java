package top.egon.mario.clocktower.game.action.service;

import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionRequest;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionResponse;

public interface ClocktowerAgentGameActionService {

    ClocktowerGameActionResponse submitAgentAction(Long gameId, Long agentInstanceId,
                                                   ClocktowerGameActionRequest request);
}
