package top.egon.mario.clocktower.game.action.service;

import top.egon.mario.clocktower.game.action.dto.ActorContext;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionResponse;
import top.egon.mario.clocktower.game.action.dto.GameActionCommand;

public interface ClocktowerGameActionExecutor {

    ClocktowerGameActionResponse execute(GameActionCommand command, ActorContext actor);
}
