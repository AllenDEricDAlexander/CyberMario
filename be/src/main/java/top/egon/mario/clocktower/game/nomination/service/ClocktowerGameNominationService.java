package top.egon.mario.clocktower.game.nomination.service;

import top.egon.mario.clocktower.game.action.dto.ActorContext;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionResponse;
import top.egon.mario.clocktower.game.action.dto.GameActionCommand;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;

public interface ClocktowerGameNominationService {

    ClocktowerGameActionResponse nominate(ClocktowerGamePo game,
                                          ClocktowerGameSeatPo actorSeat,
                                          GameActionCommand command,
                                          ActorContext actor);
}
