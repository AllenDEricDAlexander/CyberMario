package top.egon.mario.clocktower.game.action.service.impl;

import org.springframework.stereotype.Service;
import top.egon.mario.clocktower.game.action.dto.ActorContext;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionResponse;
import top.egon.mario.clocktower.game.action.dto.GameActionCommand;
import top.egon.mario.clocktower.game.action.service.ClocktowerGameActionExecutor;

@Service
public class ClocktowerGameActionExecutorImpl implements ClocktowerGameActionExecutor {

    @Override
    public ClocktowerGameActionResponse execute(GameActionCommand command, ActorContext actor) {
        return switch (command.actionType()) {
            case "PUBLIC_SPEECH", "FINISH_SPEECH", "PASS", "NOMINATE", "VOTE" ->
                    ClocktowerGameActionResponse.rejected("CLOCKTOWER_ACTION_NOT_IMPLEMENTED");
            default -> ClocktowerGameActionResponse.rejected("UNKNOWN_ACTION_TYPE");
        };
    }
}
